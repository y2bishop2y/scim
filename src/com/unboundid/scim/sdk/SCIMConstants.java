/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim.sdk;



/**
 * This class defines a number of constants used in Simple Cloud Identity
 * Management (SCIM) interfaces.
 */
public final class SCIMConstants
{
  /**
   * Prevent this class from being instantiated.
   */
  private SCIMConstants()
  {
    // No implementation is required.
  }



  /**
   * The URI of the SCIM Core schema.
   */
  public static final String SCHEMA_URI_CORE =
      "urn:scim:schemas:core:1.0";

  /**
   * The URI of the SCIM Enterprise User schema extension.
   */
  public static final String SCHEMA_URI_ENTERPRISE_USER =
      "urn:scim:schemas:extension:user:enterprise:1.0";

  /**
   * The resource name for the User resource in the core schema.
   */
  public static final String RESOURCE_NAME_USER = "User";

  /**
   * The end point for Users in the REST protocol.
   */
  public static final String RESOURCE_ENDPOINT_USERS = "Users";

  /**
   * The HTTP query parameter used in a URI to select specific SCIM attributes.
   */
  public static final String QUERY_PARAMETER_ATTRIBUTES = "attributes";

  /**
   * The HTTP query parameter used in a URI to filter by a SCIM attribute.
   */
  public static final String QUERY_PARAMETER_FILTER_BY = "filterBy";

  /**
   * The HTTP query parameter used in a URI to specify the filter comparison
   * method.
   */
  public static final String QUERY_PARAMETER_FILTER_OP = "filterOp";

  /**
   * The HTTP query parameter used in a URI to specify the filter value.
   */
  public static final String QUERY_PARAMETER_FILTER_VALUE = "filterValue";

  /**
   * The HTTP query parameter used in a URI to sort by a SCIM attribute.
   */
  public static final String QUERY_PARAMETER_SORT_BY = "sortBy";

  /**
   * The HTTP query parameter used in a URI to specify the sort order.
   */
  public static final String QUERY_PARAMETER_SORT_ORDER = "sortOrder";

  /**
   * The HTTP query parameter used in a URI to specify the starting index
   * for page results.
   */
  public static final String QUERY_PARAMETER_PAGE_START_INDEX = "startIndex";

  /**
   * The HTTP query parameter used in a URI to specify the maximum size of
   * a page of results.
   */
  public static final String QUERY_PARAMETER_PAGE_SIZE = "count";

  /**
   * The name of the JSON media type for HTTP.
   */
  public static final String MEDIA_TYPE_JSON = "application/json";

  /**
   * The name of the XML media type for HTTP.
   */
  public static final String MEDIA_TYPE_XML = "application/xml";

  /**
   * The name of the HTTP Accept header field.
   */
  public static final String HEADER_NAME_ACCEPT = "Accept";

  /**
   * The name of the HTTP Location header field.
   */
  public static final String HEADER_NAME_LOCATION = "Location";

  /**
   * The name of the HTTP Method Override field.
   */
  public static final String HEADER_NAME_METHOD_OVERRIDE =
      "X-HTTP-Method-Override";

  /**
   * The character that separates the schema URI from the basic attribute name
   * in a fully qualified attribute name. e.g. urn:scim:schemas:core:1.0:name
   * TODO: Should it be ':' or '.'?
   */
  public static final char SEPARATOR_CHAR_QUALIFIED_ATTRIBUTE = ':';
}
