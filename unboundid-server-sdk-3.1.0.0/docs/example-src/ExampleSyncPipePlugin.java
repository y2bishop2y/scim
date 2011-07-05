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



import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

import com.unboundid.directory.sdk.sync.api.SyncPipePlugin;
import com.unboundid.directory.sdk.sync.config.SyncPipePluginConfig;
import com.unboundid.directory.sdk.sync.types.PostStepResult;
import com.unboundid.directory.sdk.sync.types.SyncOperation;
import com.unboundid.directory.sdk.sync.types.SyncServerContext;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.util.args.ArgumentException;
import com.unboundid.util.args.ArgumentParser;
import com.unboundid.util.args.BooleanValueArgument;
import com.unboundid.util.args.StringArgument;



/**
 * This class provides a simple example of a sync pipe plugin which will
 * convert source attribute values to destination attribute values
 * using a fixed mapping.  Attribute values that do not appear in the mapping
 * will be copied across unmodified.  It takes the following arguments:
 * <UL>
 *   <LI>source-attribute -- The single source attribute to translate values
 *                           from.</LI>
 *   <LI>destination-attribute -- The single destination attribute to
 *                                translate values into.</LI>
 *   <LI>source-value -- An ordered list of source attribute values.</LI>
 *   <LI>destination-value -- An ordered list of equivalent destination
 *                            attribute values.</LI>
 *   <LI>case-sensitive -- Whether a case-sensitive comparison is used when
 *                         determining if a source attribute value matches.</LI>
 * </UL>
 *
 * Source attribute values that appear in the source-value list will be
 * replaced with the destination attribute value, that appears at the same
 * place in the list.
 * <p>
 * For example, this can be used to convert values that
 * have a different format on the source and destination that cannot be
 * handled by the built-in attribute mappings, for instance, if the source
 * attribute 'enabled' stores a boolean as 'Y' or 'N', but the destination
 * attribute 'is-enabled' stores it as 'true' or 'false', then you could
 * create a plugin to handle this as follows:
 * <p>
 *  <code>
 *   dsconfig create-sync-pipe-plugin --plugin-name "Example Sync Pipe Plugin"
 *   --type third-party
 *   --set
 *    extension-class:com.unboundid.directory.sdk.examples.ExampleSyncPipePlugin
 *   --set extension-argument:source-attribute=enabled
 *   --set extension-argument:destination-attribute=is-enabled
 *   --set extension-argument:source-value=y
 *   --set extension-argument:destination-value=true
 *   --set extension-argument:source-value=n
 *   --set extension-argument:destination-value=false
 *   --set extension-argument:case-sensitive=false
 *  </code>
 * </p>
 * And then add it into the Sync Pipe as follows:
 * <p>
 *  <code>
 *   dsconfig set-sync-pipe-prop --pipe-name "Example Sync Pipe"
 *   --add "plugin:Example Sync Pipe Plugin"
 *  </code>
 * </p>
 */
public final class ExampleSyncPipePlugin
       extends SyncPipePlugin
{
  private static final String ARG_NAME_SRC_ATTRIBUTE = "source-attribute";

  private static final String ARG_NAME_DEST_ATTRIBUTE = "destination-attribute";

  private static final String ARG_NAME_SRC_VALUE = "source-value";

  private static final String ARG_NAME_DEST_VALUE = "destination-value";

  private static final String ARG_NAME_CASE_SENSITIVE = "case-sensitive";


  // The server context for the server in which this extension is running.
  private SyncServerContext serverContext;

  // This lock ensures that the configuration is updated atomically and safely.
  private final ReadWriteLock configLock = new ReentrantReadWriteLock();
  private final Lock configReadLock = configLock.readLock();
  private final Lock configWriteLock = configLock.writeLock();

  // Maps a source attribute value to a destination attribute value.
  private Map<String,String> sourceValueToDestinationValue;

  // The name of the source attribute.
  private String sourceAttribute;

  // The name of the destination attribute.
  private String destinationAttribute;

  /**
   * Creates a new instance of this sync pipe plugin.  All sync pipe
   * plugins implementations must include a default constructor, but any
   * initialization should generally be done in the
   * {@code initializeSyncPipePlugin} method.
   */
  public ExampleSyncPipePlugin()
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
    return "Example Sync Pipe Plugin";
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
      "This sync pipe plugin serves as an example that may be used to " +
      "demonstrate the process for creating a third-party sync pipe plugin.  " +
      "It will perform attribute value mapping that cannot be done by the " +
      "built-in attribute value mappings.  In particular, it can map " +
      "specific source attribute values to specific destination attribute " +
      "values."
    };
  }



  /**
   * Updates the provided argument parser to define any configuration arguments
   * which may be used by this sync pipe plugin.  The argument parser may
   * also be updated to define relationships between arguments (e.g., to specify
   * required, exclusive, or dependent argument sets).
   *
   * @param  parser  The argument parser to be updated with the configuration
   *                 arguments which may be used by this sync pipe plugin.
   *
   * @throws  ArgumentException  If a problem is encountered while updating the
   *                             provided argument parser.
   */
  @Override()
  public void defineConfigArguments(final ArgumentParser parser)
         throws ArgumentException
  {
    // Add an argument that allows you to specify the source attribute.
    Character shortIdentifier = null;
    String    longIdentifier  = ARG_NAME_SRC_ATTRIBUTE;
    boolean   required        = true;
    int       maxOccurrences  = 1;
    String    placeholder     = "{src-attr}";
    String    description     = "The name of the source attribute to map " +
         "values for.";

    StringArgument arg = new StringArgument(shortIdentifier, longIdentifier,
         required, maxOccurrences, placeholder, description);
    arg.setValueRegex(Pattern.compile("^[a-zA-Z][a-zA-Z0-9\\\\-]*$"),
                      "A valid attribute name is required.");
    parser.addArgument(arg);


    // Add an argument that allows you to specify the destination attribute.
    shortIdentifier = null;
    longIdentifier  = ARG_NAME_DEST_ATTRIBUTE;
    required        = true;
    maxOccurrences  = 1;
    placeholder     = "{dest-attr}";
    description     = "The name that the destination attribute to map values " +
         "into.";

    arg = new StringArgument(shortIdentifier, longIdentifier,
                  required, maxOccurrences, placeholder, description);
    arg.setValueRegex(Pattern.compile("^[a-zA-Z][a-zA-Z0-9\\\\-]*$"),
                      "A valid attribute name is required.");
    parser.addArgument(arg);


    // Add an argument that allows you to specify a source attribute value.
    shortIdentifier = null;
    longIdentifier  = ARG_NAME_SRC_VALUE;
    required        = false;
    maxOccurrences  = Integer.MAX_VALUE;
    placeholder     = "{src-value}";
    description     = "A value of the source attribute.";

    parser.addArgument(new StringArgument(shortIdentifier, longIdentifier,
         required, maxOccurrences, placeholder, description));


    // Add an argument that allows you to specify a destination attribute value.
    shortIdentifier = null;
    longIdentifier  = ARG_NAME_DEST_VALUE;
    required        = false;
    maxOccurrences  = Integer.MAX_VALUE;
    placeholder     = "{dest-value}";
    description     = "A value of the destination attribute.";

    parser.addArgument(new StringArgument(shortIdentifier, longIdentifier,
         required, maxOccurrences, placeholder, description));


    // Add an argument that allows you to specify a destination attribute value.
    shortIdentifier = null;
    longIdentifier  = ARG_NAME_CASE_SENSITIVE;
    required        = false;
    maxOccurrences  = Integer.MAX_VALUE;
    placeholder     = "{true|false}";
    description     = "Specifies whether matching of the source attribute " +
         "should be done case-sensitively.";
    boolean isCaseSensitiveDefault = false;

    // Note:  Using BooleanValueArgument is preferable to BooleanArgument here
    // because of the way that the value is set in the server's configuration.
    parser.addArgument(new BooleanValueArgument(shortIdentifier, longIdentifier,
         required, placeholder, description, isCaseSensitiveDefault));
  }



  /**
   * Initializes this sync pipe plugin.
   *
   * @param  serverContext  A handle to the server context for the server in
   *                        which this extension is running.
   * @param  config         The general configuration for this sync pipe plugin.
   * @param  parser         The argument parser which has been initialized from
   *                        the configuration for this sync pipe plugin.
   *
   * @throws  LDAPException  If a problem occurs while initializing this sync
   *                         pipe plugin.
   */
  @Override()
  public void initializeSyncPipePlugin(
                   final SyncServerContext serverContext,
                   final SyncPipePluginConfig config,
                   final ArgumentParser parser)
         throws LDAPException
  {
    serverContext.debugInfo("Beginning sync pipe plugin initialization");

    this.serverContext = serverContext;

    setConfig(config, parser, false);
  }



  /**
   * Sets the configuration for this plugin.  This is a centralized place
   * where the configuration is initialized or updated.
   *
   * @param  config         The general configuration for this sync pipe plugin.
   * @param  parser         The argument parser which has been initialized from
   *                        the configuration for this sync pipe plugin.
   * @param  validateOnly   If true, then the configuration is only validated
   *                        and not updated.
   *
   * @throws  LDAPException  If a problem occurs while initializing this sync
   *                         pipe plugin.
   */
  private void setConfig(
                   final SyncPipePluginConfig config,
                   final ArgumentParser parser,
                   final boolean validateOnly)
         throws LDAPException
  {
    boolean isCaseSensitive = ((BooleanValueArgument)parser.getNamedArgument(
            ARG_NAME_CASE_SENSITIVE)).getValue();

    Map<String,String> valueMap;
    if (isCaseSensitive)
    {
      valueMap = new TreeMap<String,String>();
    }
    else
    {
      valueMap = new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);
    }

    String srcAttribute = ((StringArgument)parser.getNamedArgument(
            ARG_NAME_SRC_ATTRIBUTE)).getValue();
    String destAttribute = ((StringArgument)parser.getNamedArgument(
            ARG_NAME_DEST_ATTRIBUTE)).getValue();

    List<String> sourceValues = ((StringArgument)parser.getNamedArgument(
            ARG_NAME_SRC_VALUE)).getValues();
    List<String> destValues = ((StringArgument)parser.getNamedArgument(
            ARG_NAME_DEST_VALUE)).getValues();

    if (sourceValues.size() != destValues.size())
    {
      throw new LDAPException(ResultCode.PARAM_ERROR, "The same number of " +
              "values must be provided for the " + ARG_NAME_SRC_ATTRIBUTE +
              " and the " + ARG_NAME_DEST_ATTRIBUTE + ".");
    }

    Iterator<String> sourceValueIter = sourceValues.iterator();
    Iterator<String> destValueIter = destValues.iterator();

    while (sourceValueIter.hasNext())
    {
      valueMap.put(sourceValueIter.next(), destValueIter.next());
    }

    if (sourceValues.size() != valueMap.size())
    {
      throw new LDAPException(ResultCode.PARAM_ERROR, "Duplicate values were " +
              "provided for " + ARG_NAME_SRC_ATTRIBUTE + ".");
    }


    if (!validateOnly)
    {
      configWriteLock.lock();
      try
      {
        this.sourceAttribute = srcAttribute;
        this.destinationAttribute = destAttribute;
        this.sourceValueToDestinationValue = valueMap;
      }
      finally
      {
        configWriteLock.unlock();
      }
    }
  }



  /**
   * Indicates whether the configuration contained in the provided argument
   * parser represents a valid configuration for this extension.
   *
   * @param  config               The general configuration for this sync pipe
   *                              plugin.
   * @param  parser               The argument parser which has been initialized
   *                              with the proposed configuration.
   * @param  unacceptableReasons  A list that can be updated with reasons that
   *                              the proposed configuration is not acceptable.
   *
   * @return  {@code true} if the proposed configuration is acceptable, or
   *          {@code false} if not.
   */
  @Override()
  public boolean isConfigurationAcceptable(
                      final SyncPipePluginConfig config,
                      final ArgumentParser parser,
                      final List<String> unacceptableReasons)
  {
    try
    {
      setConfig(config, parser, true);
    }
    catch (LDAPException e)
    {
      unacceptableReasons.add(e.getExceptionMessage());
      return false;
    }

    return true;
  }



  /**
   * Attempts to apply the configuration contained in the provided argument
   * parser.
   *
   * @param  config                The general configuration for this sync pipe
   *                               plugin.
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
  public ResultCode applyConfiguration(final SyncPipePluginConfig config,
                                       final ArgumentParser parser,
                                       final List<String> adminActionsRequired,
                                       final List<String> messages)
  {
    try
    {
      setConfig(config, parser, false);
    }
    catch (LDAPException e)
    {
      messages.add(e.getExceptionMessage());
      return e.getResultCode();
    }

    return ResultCode.SUCCESS;
  }



  /**
   * This method is called immediately after the attributes and DN in
   * the source entry are mapped into the equivalent destination entry.
   * Once this mapping is complete, this equivalent destination entry is then
   * compared to the actual destination entry to determine which of the modified
   * attributes need to be updated.
   * <p>
   * This method is typically used to manipulate the equivalent destination
   * entry before these necessary changes are calculated.  It can also be used
   * to filter out certain changes (by returning
   * {@link PostStepResult#ABORT_OPERATION}), but this is typically done in
   * the {@link #preMapping method}.
   * <p>
   * The set of source attributes that should be synchronized at the destination
   * can be manipulated by calling
   * {@link SyncOperation#addModifiedSourceAttribute} and
   * {@link SyncOperation#removeModifiedSourceAttribute} on
   * {@code operation}.
   * <p>
   * Additional steps must be taken if this plugin adds destination attributes
   * in {@code equivalentDestinationEntry} that need to be modified at the
   * destination.  For operations with an operation type of
   * {@link com.unboundid.directory.sdk.sync.types.SyncOperationType#CREATE},
   * any updates made to
   * {@code equivalentDestinationEntry} will be included in the
   * entry created at the destination.  However, for operations with an
   * operation type of
   * {@link com.unboundid.directory.sdk.sync.types.SyncOperationType#MODIFY},
   * destination attributes
   * added by this plugin that need to be modified must be updated
   * explicitly by calling
   * {@link SyncOperation#addModifiedDestinationAttribute}.
   * <p>
   * With the exception of aborting changes or skipping the mapping step
   * completely, most plugins will override this method instead of
   * {@link #preMapping} because this method has access to the fully mapped
   * destination entry.
   *
   * @param  sourceEntry                 The entry that was fetched from the
   *                                     source.
   * @param  equivalentDestinationEntry  The destination entry that is
   *                                     equivalent to the source.  This entry
   *                                     will include all attributes mapped
   *                                     from the source entry.
   * @param  operation                   The operation that is being
   *                                     synchronized.
   *
   * @return  The result of the plugin processing.
   */
  @Override
  public PostStepResult postMapping(final Entry sourceEntry,
                                    final Entry equivalentDestinationEntry,
                                    final SyncOperation operation)
  {
    configReadLock.lock();

    String[] srcValues = sourceEntry.getAttributeValues(sourceAttribute);

    try
    {
      if (srcValues != null)
      {
        ArrayList<String> destValues = new ArrayList<String>(srcValues.length);
        for (String srcValue: srcValues)
        {
          String destValue = sourceValueToDestinationValue.get(srcValue);
          if (destValue == null)
          {
            // Defaults to the source value if there isn't a mapping.
            destValue = srcValue;
          }
          destValues.add(destValue);

          // Set the destination attribute in the entry.
          equivalentDestinationEntry.setAttribute(destinationAttribute,
                  destValues.toArray(new String[0]));
        }
      }
      else
      {
        equivalentDestinationEntry.removeAttribute(destinationAttribute);
      }

      // If we don't set this here, then the Sync Pipe won't know that it needs
      // to update the destination attribute.
      operation.addModifiedDestinationAttribute(destinationAttribute);

      // This keeps the source attribute from being copied over as is into
      // a destination attribute with the same name.
      operation.removeModifiedSourceAttribute(sourceAttribute);

      operation.logInfo("Mapped values for " + sourceAttribute + " to " +
                        destinationAttribute + " in plugin.");

      return PostStepResult.CONTINUE;
    }
    finally
    {
      configReadLock.unlock();
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
              ARG_NAME_SRC_ATTRIBUTE + "=enabled",
              ARG_NAME_DEST_ATTRIBUTE + "=is-enabled",
              ARG_NAME_SRC_VALUE + "=y",
              ARG_NAME_DEST_VALUE + "=true",
              ARG_NAME_SRC_VALUE + "=n",
              ARG_NAME_DEST_VALUE + "=false"),
         "Map source attribute 'enabled' to destination attribute 'is-enabled',"
         + " replacing 'y' with 'true' and 'n' with 'false'.");

    return exampleMap;
  }



  /**
   * Appends a string representation of this sync pipe plugin to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the string representation should be
   *                 appended.
   */
  @Override()
  public void toString(final StringBuilder buffer)
  {
    buffer.append("ExampleSyncPipePlugin(sourceAttribute='");
    buffer.append(sourceAttribute);
    buffer.append("', destinationAttribute='");
    buffer.append(destinationAttribute);
    buffer.append("', sourceValueToDestinationValue='");
    buffer.append(sourceValueToDestinationValue);
    buffer.append("')");
  }
}