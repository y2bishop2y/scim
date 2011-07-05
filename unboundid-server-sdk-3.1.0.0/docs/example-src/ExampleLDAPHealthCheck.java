/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * docs/licenses/cddl.txt
 * or http://www.opensource.org/licenses/cddl1.php.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * docs/licenses/cddl.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010-2011 UnboundID Corp.
 */
package com.unboundid.directory.sdk.examples;



import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.unboundid.directory.sdk.proxy.api.LDAPHealthCheck;
import com.unboundid.directory.sdk.proxy.config.LDAPHealthCheckConfig;
import com.unboundid.directory.sdk.proxy.types.BackendServer;
import com.unboundid.directory.sdk.proxy.types.CompletedProxyOperationContext;
import com.unboundid.directory.sdk.proxy.types.HealthCheckResult;
import com.unboundid.directory.sdk.proxy.types.HealthCheckState;
import com.unboundid.directory.sdk.proxy.types.ProxyServerContext;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.StaticUtils;
import com.unboundid.util.args.ArgumentException;
import com.unboundid.util.args.ArgumentParser;
import com.unboundid.util.args.DNArgument;
import com.unboundid.util.args.DurationArgument;



/**
 * This class provides a simple example of an LDAP health check which simply
 * attempts to retrieve a specified entry from the backend server.  The length
 * of time required to retrieve the entry will be used to help determine the
 * health check score.  It has the following configuration arguments:
 * <UL>
 *   <LI>entry-dn -- The DN of the entry to retrieve.</LI>
 *   <LI>max-available-response-time -- The maximum search duration to consider
 *       a server available.  Any duration longer than this will cause the
 *       server to be considered either degraded or unavailable.</LI>
 *   <LI>max-degraded-response-time -- The maximum search duration to consider a
 *       server degraded.  Any duration longer than this will cause the server
 *       to be considered unavailable.</LI>
 * </UL>
 */
public final class ExampleLDAPHealthCheck
       extends LDAPHealthCheck
{
  /**
   * The name of the argument that will be used to specify the DN of the entry
   * to retrieve.
   */
  private static final String ARG_NAME_ENTRY_DN = "entry-dn";



  /**
   * The name of the argument that will be used to specify the maximum available
   * response time.
   */
  private static final String ARG_NAME_MAX_AVAILABLE_DURATION =
       "max-available-response-time";



  /**
   * The name of the argument that will be used to specify the maximum degraded
   * response time.
   */
  private static final String ARG_NAME_MAX_DEGRADED_DURATION =
       "max-degraded-response-time";



  // The maximum available duration.
  private volatile long maxAvailableDuration;

  // The maximum degraded duration.
  private volatile long maxDegradedDuration;

  // The server context for the server in which this extension is running.
  private ProxyServerContext serverContext;

  // The DN of the entry to retrieve.
  private volatile String entryDN;



  /**
   * Creates a new instance of this LDAP health check.  All LDAP health check
   * implementations must include a default constructor, but any initialization
   * should generally be done in the {@code initializeLDAPHealthCheck} method.
   */
  public ExampleLDAPHealthCheck()
  {
    // No implementation required.
  }



  /**
   * Retrieves a human-readable name for this extension.
   *
   * @return  A human-readable name for this extension.
   */
  @Override()
  public String getExtensionName()
  {
    return "Example LDAP Health Check";
  }



  /**
   * Retrieves a human-readable description for this extension.  Each element
   * of the array that is returned will be considered a separate paragraph in
   * generated documentation.
   *
   * @return  A human-readable description for this extension, or {@code null}
   *          or an empty array if no description should be available.
   */
  @Override()
  public String[] getExtensionDescription()
  {
    return new String[]
    {
      "This LDAP health check serves as an example that may be used to " +
           "demonstrate the process for creating a third-party health " +
           "check.  It will perform a search in order to retrieve a " +
           "entry from a backend server, and any problem encountered while " +
           "attempting to retrieve that entry will cause the server to be " +
           "considered unavailable.  If the entry is retrieved successfully, " +
           "then the length of time required to retrieve it may be used to " +
           "classify the server as either available or degraded, and it will " +
           "also be used to generate the score that can help rank the server " +
           "relative to other servers with the same state."
    };
  }



  /**
   * Updates the provided argument parser to define any configuration arguments
   * which may be used by this LDAP health check.  The argument parser may also
   * be updated to define relationships between arguments (e.g., to specify
   * required, exclusive, or dependent argument sets).
   *
   * @param  parser  The argument parser to be updated with the configuration
   *                 arguments which may be used by this LDAP health check.
   *
   * @throws  ArgumentException  If a problem is encountered while updating the
   *                             provided argument parser.
   */
  @Override()
  public void defineConfigArguments(final ArgumentParser parser)
         throws ArgumentException
  {
    // Add an argument that allows you to specify the target entry.
    Character shortIdentifier = null;
    String    longIdentifier  = ARG_NAME_ENTRY_DN;
    boolean   required        = true;
    int       maxOccurrences  = 1;
    String    placeholder     = "{dn}";
    String    description     = "The DN of the entry to retrieve during the " +
         "course of health check processing.";

    parser.addArgument(new DNArgument(shortIdentifier, longIdentifier,
         required, maxOccurrences, placeholder, description));


    // Add an argument that allows you to specify the maximum available
    // duration.
    shortIdentifier = null;
    longIdentifier  = ARG_NAME_MAX_AVAILABLE_DURATION;
    required        = true;
    placeholder     = "{duration}";
    description     = "The maximum length of time that a health check search " +
         "may take for a server to be considered available.  The value " +
         "should consist of an integer followed by a time unit (e.g., " +
         "'10 ms').";

    Long     defaultValue    = null;
    TimeUnit defaultUnit     = null;
    Long     lowerBoundValue = 1L;
    TimeUnit lowerBoundUnit  = TimeUnit.MILLISECONDS;
    Long     upperBoundValue = null;
    TimeUnit upperBoundUnit  = null;

    parser.addArgument(new DurationArgument(shortIdentifier, longIdentifier,
         required, placeholder, description, defaultValue, defaultUnit,
         lowerBoundValue, lowerBoundUnit, upperBoundValue, upperBoundUnit));


    // Add an argument that allows you to specify the maximum degraded
    // duration.
    shortIdentifier = null;
    longIdentifier  = ARG_NAME_MAX_DEGRADED_DURATION;
    required        = true;
    placeholder     = "{duration}";
    description     = "The maximum length of time that a health check search " +
         "may take for a server to be considered degraded.  The value " +
         "should consist of an integer followed by a time unit (e.g., " +
         "'10 ms').";
    defaultValue    = null;
    defaultUnit     = null;
    lowerBoundValue = 2L;
    lowerBoundUnit  = TimeUnit.MILLISECONDS;
    upperBoundValue = null;
    upperBoundUnit  = null;

    parser.addArgument(new DurationArgument(shortIdentifier, longIdentifier,
         required, placeholder, description, defaultValue, defaultUnit,
         lowerBoundValue, lowerBoundUnit, upperBoundValue, upperBoundUnit));
  }



  /**
   * Initializes this LDAP health check.
   *
   * @param  serverContext  A handle to the server context for the server in
   *                        which this extension is running.
   * @param  config         The general configuration for this LDAP health
   *                        check.
   * @param  parser         The argument parser which has been initialized from
   *                        the configuration for this LDAP health check.
   *
   * @throws  LDAPException  If a problem occurs while initializing this LDAP
   *                         health check.
   */
  @Override()
  public void initializeLDAPHealthCheck(final ProxyServerContext serverContext,
                                        final LDAPHealthCheckConfig config,
                                        final ArgumentParser parser)
         throws LDAPException
  {
    serverContext.debugInfo("Beginning LDAP health check initialization");

    this.serverContext = serverContext;

    // Get the target entry DN.
    final DNArgument dnArg =
         (DNArgument) parser.getNamedArgument(ARG_NAME_ENTRY_DN);
    entryDN = dnArg.getValue().toString();

    // Get the maximum available response time.
    final DurationArgument maxAvailableArg =
         (DurationArgument)
         parser.getNamedArgument(ARG_NAME_MAX_AVAILABLE_DURATION);
    maxAvailableDuration = maxAvailableArg.getValue(TimeUnit.MILLISECONDS);

    // Get the maximum degraded response time.
    final DurationArgument maxDegradedArg =
         (DurationArgument)
         parser.getNamedArgument(ARG_NAME_MAX_DEGRADED_DURATION);
    maxDegradedDuration = maxDegradedArg.getValue(TimeUnit.MILLISECONDS);


    // The maximum available response time must be less than or equal to the
    // maximum degraded response time.
    if (maxAvailableDuration > maxDegradedDuration)
    {
      throw new LDAPException(ResultCode.PARAM_ERROR,
           "The maximum available duration must be less than or equal to the " +
                "maximum degraded duration.");
    }
  }



  /**
   * Indicates whether the configuration contained in the provided argument
   * parser represents a valid configuration for this extension.
   *
   * @param  config               The general configuration for this LDAP health
   *                              check.
   * @param  parser               The argument parser which has been initialized
   *                              with the proposed configuration.
   * @param  unacceptableReasons  A list that can be updated with reasons that
   *                              the proposed configuration is not acceptable.
   *
   * @return  {@code true} if the proposed configuration is acceptable, or
   *          {@code false} if not.
   */
  @Override()
  public boolean isConfigurationAcceptable(final LDAPHealthCheckConfig config,
                      final ArgumentParser parser,
                      final List<String> unacceptableReasons)
  {
    boolean acceptable = true;

    // The maximum available response time must be less than or equal to the
    // maximum degraded response time.
    final DurationArgument maxAvailableArg =
         (DurationArgument)
         parser.getNamedArgument(ARG_NAME_MAX_AVAILABLE_DURATION);
    final long maxAvailable = maxAvailableArg.getValue(TimeUnit.MILLISECONDS);

    final DurationArgument maxDegradedArg =
         (DurationArgument)
         parser.getNamedArgument(ARG_NAME_MAX_DEGRADED_DURATION);
    final long maxDegraded = maxDegradedArg.getValue(TimeUnit.MILLISECONDS);

    if (maxAvailable > maxDegraded)
    {
      unacceptableReasons.add("The maximum available duration must be less " +
           "than or equal to the maximum degraded duration.");
      acceptable = false;
    }


    return acceptable;
  }



  /**
   * Attempts to apply the configuration contained in the provided argument
   * parser.
   *
   * @param  config                The general configuration for this LDAP
   *                               health check.
   * @param  parser                The argument parser which has been
   *                               initialized with the new configuration.
   * @param  adminActionsRequired  A list that can be updated with information
   *                               about any administrative actions that may be
   *                               required before one or more of the
   *                               configuration changes will be applied.
   * @param  messages              A list that can be updated with information
   *                               about the result of applying the new
   *                               configuration.
   *
   * @return  A result code that provides information about the result of
   *          attempting to apply the configuration change.
   */
  @Override()
  public ResultCode applyConfiguration(final LDAPHealthCheckConfig config,
                                       final ArgumentParser parser,
                                       final List<String> adminActionsRequired,
                                       final List<String> messages)
  {
    // Get the target entry DN.
    final DNArgument dnArg =
         (DNArgument) parser.getNamedArgument(ARG_NAME_ENTRY_DN);
    final String newDN = dnArg.getValue().toString();

    // Get the maximum available response time.
    final DurationArgument maxAvailableArg =
         (DurationArgument)
         parser.getNamedArgument(ARG_NAME_MAX_AVAILABLE_DURATION);
    final long newAvailable = maxAvailableArg.getValue(TimeUnit.MILLISECONDS);

    // Get the maximum degraded response time.
    final DurationArgument maxDegradedArg =
         (DurationArgument)
         parser.getNamedArgument(ARG_NAME_MAX_DEGRADED_DURATION);
    final long newDegraded = maxDegradedArg.getValue(TimeUnit.MILLISECONDS);


    entryDN              = newDN;
    maxAvailableDuration = newAvailable;
    maxDegradedDuration  = newDegraded;

    return ResultCode.SUCCESS;
  }



  /**
   * Performs any cleanup which may be necessary when this LDAP health check is
   * to be taken out of service.
   */
  @Override()
  public void finalizeLDAPHealthCheck()
  {
    // No finalization is required.
  }



  /**
   * Attempts to determine the health of the provided LDAP external server whose
   * last health check result indicated that the server had a state of
   * AVAILABLE.  This method may be periodically invoked for AVAILABLE servers
   * to determine whether their state has changed.
   *
   * @param  backendServer  A handle to the LDAP external server whose health is
   *                        to be assessed.
   * @param  connection     A connection that may be used to communicate with
   *                        the server in the course of performing the
   *                        evaluation.  The health check should not do anything
   *                        which may alter the state of this connection.
   *
   * @return  Information about the result of the health check.
   */
  @Override()
  public HealthCheckResult checkAvailableServer(
                                final BackendServer backendServer,
                                final LDAPConnection connection)
  {
    return checkServer(connection);
  }



  /**
   * Attempts to determine the health of the provided LDAP external server whose
   * last health check result indicated that the server had a state of DEGRADED.
   * This method may be periodically invoked for DEGRADED servers to determine
   * whether their state has changed.
   *
   * @param  backendServer  A handle to the LDAP external server whose health is
   *                        to be assessed.
   * @param  connection     A connection that may be used to communicate with
   *                        the server in the course of performing the
   *                        evaluation.  The health check should not do anything
   *                        which may alter the state of this connection.
   *
   * @return  Information about the result of the health check.
   */
  @Override()
  public HealthCheckResult checkDegradedServer(
                                final BackendServer backendServer,
                                final LDAPConnection connection)
  {
    return checkServer(connection);
  }



  /**
   * Attempts to determine the health of the provided LDAP external server whose
   * last health check result indicated that the server had a state of
   * UNAVAILABLE.  This method may be periodically invoked for UNAVAILABLE
   * servers to determine whether their state has changed.
   *
   * @param  backendServer  A handle to the LDAP external server whose health is
   *                        to be assessed.
   * @param  connection     A connection that may be used to communicate with
   *                        the server in the course of performing the
   *                        evaluation.  The health check should not do anything
   *                        which may alter the state of this connection.
   *
   * @return  Information about the result of the health check.
   */
  @Override()
  public HealthCheckResult checkUnavailableServer(
                                final BackendServer backendServer,
                                final LDAPConnection connection)
  {
    return checkServer(connection);
  }



  /**
   * Attempts to determine the health of the provided LDAP external server in
   * which an attempted operation did not complete successfully.
   *
   * @param  operationContext  A handle to the operation context for the
   *                           operation that failed.
   * @param  exception         The exception caught when attempting to process
   *                           the associated operation in the backend server.
   * @param  backendServer     A handle to the backend server in which the
   *                           operation was processed.
   *
   * @return  Information about the result of the health check.
   */
  @Override()
  public HealthCheckResult checkFailedOperation(
              final CompletedProxyOperationContext operationContext,
              final LDAPException exception,
              final BackendServer backendServer)
  {
    // Look at the result code to see if it indicates that the server might not
    // be available.
    if (exception.getResultCode().isConnectionUsable())
    {
      // The result code indicates that the connection is probably usable, so
      // we'll just return whatever the last known result was.
      return backendServer.getHealthCheckResult();
    }


    // The server might not be usable.  See if we can establish a connection to
    // it.
    final LDAPConnection connection;
    try
    {
      connection = backendServer.createNewConnection(null,
           "Example Health Check for failed operation " +
                operationContext.toString());
    }
    catch (final Exception e)
    {
      // We can't establish a connection, so we have to consider the server
      // unavailable.
      serverContext.debugCaught(e);
      return serverContext.createHealthCheckResult(
           HealthCheckState.UNAVAILABLE, 0,
           "Unable to establish a connection to the server:  " +
                StaticUtils.getExceptionMessage(e));
    }


    // Use the connection to perform the health check.
    try
    {
      return checkServer(connection);
    }
    finally
    {
      connection.close();
    }
  }



  /**
   * Performs a search to assess the health of the backend server using the
   * given connection.
   *
   * @param  connection  The connection to use to communicate with the server.
   *
   * @return  The health check result representing the assessed health of the
   *          server.
   */
  private HealthCheckResult checkServer(final LDAPConnection connection)
  {
    // Create local copies of the config variables.
    final String dn = entryDN;
    final long maxA = maxAvailableDuration;
    final long maxD = maxDegradedDuration;


    // Construct a search request to use for the health check.
    final SearchRequest searchRequest = new SearchRequest(dn, SearchScope.BASE,
         Filter.createPresenceFilter("objectClass"),
         SearchRequest.NO_ATTRIBUTES);
    searchRequest.setResponseTimeoutMillis(maxDegradedDuration);


    // Get the start time.
    final long startTime = System.currentTimeMillis();


    // Perform a search to retrieve the target entry
    SearchResult searchResult;
    try
    {
      searchResult = connection.search(searchRequest);
    }
    catch (final LDAPSearchException lse)
    {
      serverContext.debugCaught(lse);
      searchResult = lse.getSearchResult();
    }


    // Get the stop time.
    final long stopTime = System.currentTimeMillis();


    // If the result code is anything other than success, then we'll consider
    // the server unavailable.
    if (searchResult.getResultCode() != ResultCode.SUCCESS)
    {
      return serverContext.createHealthCheckResult(
           HealthCheckState.UNAVAILABLE, 0,
           "Example health check search returned a non-success result of " +
                searchResult.toString());
    }


    // Figure out how long the search took and use that to determine the state
    // and score to use for the server.
    final long elapsedTime = stopTime - startTime;
    if (elapsedTime <= maxA)
    {
      final int score = (int) Math.round(10.0d - (10.0d * elapsedTime / maxA));
      return serverContext.createHealthCheckResult(
           HealthCheckState.AVAILABLE, score);
    }
    else if (elapsedTime <= maxD)
    {
      final int score = (int) Math.round(10.0d - (10.0d * elapsedTime / maxD));
      return serverContext.createHealthCheckResult(
           HealthCheckState.DEGRADED, score,
           "Example health check duration exceeded the maximum available " +
                "response time of " + maxA + "ms");
    }
    else
    {
      return serverContext.createHealthCheckResult(
           HealthCheckState.UNAVAILABLE, 0,
           "Example health check duration exceeded the maximum degraded " +
                "response time of " + maxD + "ms");
    }
  }



  /**
   * Retrieves a map containing examples of configurations that may be used for
   * this extension.  The map key should be a list of sample arguments, and the
   * corresponding value should be a description of the behavior that will be
   * exhibited by the extension when used with that configuration.
   *
   * @return  A map containing examples of configurations that may be used for
   *          this extension.  It may be {@code null} or empty if there should
   *          not be any example argument sets.
   */
  @Override()
  public Map<List<String>,String> getExamplesArgumentSets()
  {
    final LinkedHashMap<List<String>,String> exampleMap =
         new LinkedHashMap<List<String>,String>(1);

    exampleMap.put(
         Arrays.asList(
              ARG_NAME_ENTRY_DN + "=dc=example,dc=com",
              ARG_NAME_MAX_AVAILABLE_DURATION + "=500ms",
              ARG_NAME_MAX_DEGRADED_DURATION + "=5s"),
         "Ensure that the 'dc=example,dc=com' entry can be retrieved.  If " +
              "the time required to retrieve the entry is no more than " +
              "500ms, then the server will be considered available.  If the " +
              "time required to retrieve the entry is between 500ms and 5s, " +
              "then the server will be considered degraded.  If the entry " +
              "cannot be retrieved, or if it takes more than 5s to retrieve " +
              "it, then the server will be considered unavailable.");

    return exampleMap;
  }



  /**
   * Appends a string representation of this LDAP health check to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string representation should be
   *                 appended.
   */
  @Override()
  public void toString(final StringBuilder buffer)
  {
    buffer.append("ExampleHealthCheck(dn='");
    buffer.append(entryDN);
    buffer.append("', maxAvailableResponseTime=");
    buffer.append(maxAvailableDuration);
    buffer.append("ms, maxDegradedResponseTime=");
    buffer.append(maxDegradedDuration);
    buffer.append("ms)");
  }
}