/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim.sdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;



/**
 * This class represents a list of query attributes taken from the attributes
 * query parameter. e.g. attributes=displayName,userName
 */
public class SCIMQueryAttributes
{
  /**
   * The set of attributes explicitly requested, or empty if all attributes are
   * requested.
   */
  private final List<SCIMAttributeType> types;


  /**
   * Create a new instance of query attributes from their string representation.
   *
   * @param attributes     The set of attributes requested, or empty if all
   *                       attributes are requested. The attributes must be
   *                       qualified by their schema URI if they are not in the
   *                       core schema.
   */
  public SCIMQueryAttributes(final String ... attributes)
  {
    types = new ArrayList<SCIMAttributeType>();
    if (attributes.length > 0)
    {
      for (final String a : attributes)
      {
        types.add(SCIMAttributeType.fromQualifiedName(a));
      }
    }
  }



  /**
   * Retrieve the list of query attributes.
   *
   * @return  The list of query attributes, or an empty list if all attributes
   *          are requested.
   */
  public List<SCIMAttributeType> getAttributeTypes()
  {
    return Collections.unmodifiableList(types);
  }



  /**
   * Determine whether all attributes are requested by these query attributes.
   *
   * @return  {@code true} if all attributes are requested, and {@code false}
   *          otherwise.
   */
  public boolean allAttributesRequested()
  {
    return types.isEmpty();
  }



  /**
   * Determine whether the specified attribute is requested by these query
   * attributes.
   *
   * @param type  The attribute type for which to make the determination.
   *
   * @return  {@code true} if the specified attribute is requested, or false
   *          otherwise.
   */
  public boolean isAttributeRequested(final SCIMAttributeType type)
  {
    if (allAttributesRequested())
    {
      return true;
    }

    return types.contains(type);
  }



  /**
   * Determine whether the specified attribute is requested by these query
   * attributes.
   *
   * @param schema  The URI of the schema to which the specified attribute
   *                belongs.
   * @param name    The name of the attribute for which to make the
   *                determination.
   *
   * @return  {@code true} if the specified attribute is requested, or false
   *          otherwise.
   */
  public boolean isAttributeRequested(final String schema, final String name)
  {
    if (allAttributesRequested())
    {
      return true;
    }

    return types.contains(new SCIMAttributeType(schema, name));
  }



  /**
   * Determine whether the specified core schema attribute is requested by
   * these query attributes.
   *
   * @param name    The name of the core schema attribute for which to make the
   *                determination.
   *
   * @return  {@code true} if the specified attribute is requested, or false
   *          otherwise.
   */
  public boolean isAttributeRequested(final String name)
  {
    if (allAttributesRequested())
    {
      return true;
    }

    return types.contains(new SCIMAttributeType(name));
  }
}