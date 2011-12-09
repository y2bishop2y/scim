/*
 * Copyright 2011 UnboundID Corp.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 */

package com.unboundid.scim.ldap;

import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPInterface;
import com.unboundid.scim.schema.AttributeDescriptor;
import com.unboundid.scim.sdk.Debug;
import com.unboundid.scim.sdk.InvalidResourceException;
import com.unboundid.scim.sdk.SCIMAttribute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;


/**
 * Attribute derivations may be used to derive the value of a read-only
 * SCIM attribute from any attribute of the LDAP entry representing the SCIM
 * resource and/or information from other entries anywhere in the DIT.
 * Implementations may perform any number of LDAP operations through the
 * {@link com.unboundid.ldap.sdk.LDAPInterface} API.
 * <BR><BR>
 * This is used to implement the groups SCIM attribute in User resources when
 * the directory server does not provide the isMemberOf LDAP attribute. It can
 * also be used to implement the members SCIM attribute in Group resources for
 * UnboundID dynamic groups.
 * <BR><BR>
 * To use a custom derivation class, include a <tt>derivation</tt> element with
 * the <tt>javaClass</tt> attribute inside a <tt>attribute</tt> element. For
 * example:
 * <BR><BR>
 * <PRE>
 * &lt;attribute name=&quot;exampleAttribute&quot;&gt;
 *   &lt;description&gt;Example Attribute&lt;&#47description&gt;
 *   &lt;derivation javaClass=&quot;com.example.ExampleDerivedAttr&quot;&#47&gt;
 *   &lt;simpleMultiValued tag=&quot;attr&quot; dataType=&quot;string&quot;/&gt;
 * &lt;&#47attribute&gt;
 * </PRE>
 * <BR><BR>
 * This API is volatile and could change in future releases.
 */
public abstract class DerivedAttribute
{
  /**
   * The name of the special element that may be present inside derived
   * attributes which provides a handle to a top-level LDAPSearch element.
   */
  public static final String LDAP_SEARCH_REF = "LDAPSearchRef";

  /**
   * The map of arguments provided to this derived attribute. This maps the
   * element name to its value. See getArguments() for an example.
   */
  private final Map<String,Object> arguments = new HashMap<String,Object>();

  /**
   * Create a derived attribute from the name of a class that extends the
   * {@code DerivedAttribute} abstract class.
   *
   * @param className  The name of a class that extends
   *                   {@code DerivedAttribute}.
   * @param args       The set of arguments that are configured for this
   *                   {@code DerivedAttribute}.
   *
   * @return  A new instance of the derived attribute class.
   */
  public static DerivedAttribute create(final String className,
                                        final List<Object> args)
  {
    Class clazz;
    try
    {
      clazz = Class.forName(className, true,
          DerivedAttribute.class.getClassLoader());
    }
    catch (ClassNotFoundException e)
    {
      Debug.debugException(e);
      throw new IllegalArgumentException(
          "Class '" + className + "' not found", e);
    }

    final Object object;
    try
    {
      object = clazz.newInstance();
    }
    catch (Exception e)
    {
      Debug.debugException(e);
      throw new IllegalArgumentException(
          "Cannot create instance of class '" + className + "'", e);
    }

    if (!(object instanceof DerivedAttribute))
    {
      throw new IllegalArgumentException(
          "Class '" + className + "' is not a Derived Attribute");
    }

    DerivedAttribute d = (DerivedAttribute) object;
    if(args != null)
    {
      for(Object o : args)
      {
        Element e = (Element) o;
        String name = e.getTagName();
        String value = e.getTextContent();
        if(LDAP_SEARCH_REF.equalsIgnoreCase(name) && e.hasAttribute("idref"))
        {
          //This is a special case, for LDAPSearchParameters
          value = e.getAttribute("idref");
        }
        d.arguments.put(name, value);
      }
    }
    return d;
  }



  /**
   * Initialize the derived attribute implementation.
   *
   * @param descriptor  The attribute descriptor for the derived attribute.
   */
  public abstract void initialize(final AttributeDescriptor descriptor);



  /**
   * Retrieve the attribute descriptor for the derived attribute.
   * @return  The attribute descriptor for the derived attribute.
   */
  public abstract AttributeDescriptor getAttributeDescriptor();



  /**
   * Retrieve the set of LDAP attribute types needed in the entry representing
   * the resource.
   *
   * @return  The set of LDAP attribute types needed in the entry representing
   *          the resource.
   */
  public abstract Set<String> getLDAPAttributeTypes();


  /**
   * Derive a SCIM attribute value from the provided information.
   *
   * @param entry          An LDAP entry representing the SCIM resource for
   *                       which a SCIM attribute value is to be derived.
   * @param ldapInterface  An LDAP interface that may be used to search the DIT.
   * @param searchBaseDN   The search base DN for the DIT.
   *
   * @return  A SCIM attribute, or {@code null} if no attribute was created.
   * @throws InvalidResourceException if the mapping violates the schema.
   */
  public abstract SCIMAttribute toSCIMAttribute(
      final Entry entry,
      final LDAPInterface ldapInterface,
      final String searchBaseDN) throws InvalidResourceException;


  /**
   * Returns the arguments map for this DerivedAttribute. The arguments map is
   * constructed from the child elements of the <derivation> element.
   * For example, a derivation like the following:
   * <PRE>
   *   &lt;derivation javaClass="com.example.ExampleDerivedAttr"&gt;
   *     &lt;key1&gt;value1&lt;/key1&gt;
   *     &lt;key2&gt;value2&lt;/key2&gt;
   *   &lt;/derivation&gt;
   * </PRE>
   * would have an arguments map containing 'key1' and 'key2' with values
   * 'value1' and 'value2'.
   *
   * @return a map of argument names to argument values. This is modifiable,
   *         and will never be null.
   */
  public final Map<String,Object> getArguments()
  {
    return arguments;
  }
}
