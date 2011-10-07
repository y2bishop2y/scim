/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim.ldap;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.scim.config.Schema;
import com.unboundid.scim.config.SchemaManager;
import com.unboundid.scim.sdk.SCIMAttribute;
import com.unboundid.scim.sdk.SCIMAttributeType;
import com.unboundid.scim.sdk.SCIMFilter;
import com.unboundid.scim.sdk.SCIMObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;



/**
 * This abstract class defines an interface that may be implemented to perform
 * a mapping between a SCIM attribute and LDAP attributes. There may be several
 * attribute mapper instances defined for a single SCIM attribute type, in order
 * to support, for example, different mappings for work addresses and home
 * addresses.
 */
public abstract class AttributeMapper
{
  /**
   * The SCIM attribute type that is mapped by this attribute mapper.
   */
  private SCIMAttributeType scimAttributeType;



  /**
   * The SCIM schema for the SCIM attribute type that is mapped by this
   * attribute mapper.
   */
  private final Schema schema;



  /**
   * Create a new instance of an attribute mapper.
   *
   * @param scimAttributeType  The SCIM attribute type that is mapped by this
   *                           attribute mapper.
   */
  protected AttributeMapper(final SCIMAttributeType scimAttributeType)
  {
    this.scimAttributeType = scimAttributeType;

    final SchemaManager schemaManager = SchemaManager.instance();
    schema = schemaManager.getSchema(scimAttributeType.getSchema());
  }



  /**
   * Retrieve the SCIM schema for the SCIM attribute type that is mapped by this
   * attribute mapper.
   *
   * @return  The SCIM schema for the SCIM attribute type that is mapped by this
   *          attribute mapper.
   */
  public Schema getSchema()
  {
    return schema;
  }



  /**
   * Retrieve the SCIM attribute type that is mapped by this attribute mapper.
   *
   * @return  The SCIM attribute type that is mapped by this attribute mapper.
   */
  public SCIMAttributeType getSCIMAttributeType()
  {
    return scimAttributeType;
  }



  /**
   * Retrieve the set of LDAP attribute types used by this attribute mapper.
   *
   * @return  The set of LDAP attribute types used by this attribute mapper.
   */
  public abstract Set<String> getLDAPAttributeTypes();



  /**
   * Map the provided SCIM filter to an LDAP filter.
   *
   * @param filter  The SCIM filter to be mapped. The filter identifies the
   *                SCIM attribute that is mapped by this attribute mapper,
   *                or one of its sub-attributes.
   *
   * @return  An LDAP filter.
   */
  public abstract Filter toLDAPFilter(final SCIMFilter filter);



  /**
   * Maps this SCIM attribute type to an LDAP attribute type that should be
   * used as a sort key when the SCIM attribute type has been specified in
   * sort parameters.
   *
   * @return  The LDAP attribute type that should be used as a sort key when
   *          the SCIM attribute type has been specified in sort parameters.
   *          The method returns {@code null} if the SCIM attribute type
   *          cannot be used in sort parameters.
   */
  public abstract String toLDAPSortAttributeType();



  /**
   * Map the SCIM attribute in the provided SCIM object to LDAP attributes.
   *
   * @param scimObject  The SCIM object containing the attribute to be mapped.
   * @param attributes  A collection of LDAP attributes to hold any attributes
   *                    created by this mapping.
   */
  public abstract void toLDAPAttributes(
      final SCIMObject scimObject,
      final Collection<Attribute> attributes);



  /**
   * Map the LDAP attributes in the provided LDAP entry to a SCIM attribute.
   *
   * @param entry  The LDAP entry whose attributes are to be mapped.
   *
   * @return  A SCIM attribute, or {@code null} if no attribute was created.
   */
  public abstract SCIMAttribute toSCIMAttribute(final Entry entry);



  /**
   * Create an attribute mapper from an attribute definition.
   *
   * @param attributeDefinition  The attribute definition.
   * @param resourceSchema       The resource schema URN.
   *
   * @return  An attribute mapper, or {@code null} if the attribute definition
   *          contains no mappings.
   */
  public static AttributeMapper create(
      final AttributeDefinition attributeDefinition,
      final String resourceSchema)
  {
    final String schema;
    if (attributeDefinition.getSchema() == null)
    {
      schema = resourceSchema;
    }
    else
    {
      schema = attributeDefinition.getSchema();
    }
    final SCIMAttributeType scimAttributeType =
        new SCIMAttributeType(schema, attributeDefinition.getName());

    if (attributeDefinition.getSimple() != null)
    {
      final SimpleAttributeDefinition simpleDefinition =
          attributeDefinition.getSimple();

      if (simpleDefinition.getMapping() == null)
      {
        return null;
      }

      final AttributeTransformation t =
          AttributeTransformation.create(simpleDefinition.getMapping());

      return new SimpleAttributeMapper(scimAttributeType, t);
    }
    else if (attributeDefinition.getComplex() != null)
    {
      final ComplexAttributeDefinition complexDefinition =
          attributeDefinition.getComplex();

      final List<SubAttributeTransformation> transformations =
          new ArrayList<SubAttributeTransformation>();

      for (final SubAttributeDefinition subAttributeDefinition :
          complexDefinition.getSubAttribute())
      {
        if (subAttributeDefinition.getMapping() != null)
        {
          transformations.add(
              SubAttributeTransformation.create(subAttributeDefinition));
        }
      }

      if (transformations.isEmpty())
      {
        return null;
      }

      return new ComplexSingularAttributeMapper(scimAttributeType,
                                                transformations);
    }
    else if (attributeDefinition.getSimplePlural() != null)
    {
      final SimplePluralAttributeDefinition simplePluralDefinition =
          attributeDefinition.getSimplePlural();

      final List<PluralValueMapper> pluralMappers =
          new ArrayList<PluralValueMapper>();

      for (final PluralType pluralType : simplePluralDefinition.getPluralType())
      {
        final PluralValueMapper m = PluralValueMapper.create(pluralType);
        if (m != null)
        {
          pluralMappers.add(m);
        }
      }

      if (simplePluralDefinition.getMapping() != null)
      {
        pluralMappers.add(PluralValueMapper.create(
            simplePluralDefinition.getMapping()));
      }

      if (pluralMappers.isEmpty())
      {
        return null;
      }

      return new PluralAttributeMapper(scimAttributeType, pluralMappers);
    }
    else if (attributeDefinition.getComplexPlural() != null)
    {
      final ComplexPluralAttributeDefinition complexPluralDefinition =
          attributeDefinition.getComplexPlural();

      final List<PluralValueMapper> pluralMappers =
          new ArrayList<PluralValueMapper>();

      for (final PluralType pluralType :
          complexPluralDefinition.getPluralType())
      {
        final PluralValueMapper m = PluralValueMapper.create(pluralType);
        if (m != null)
        {
          pluralMappers.add(m);
        }
      }

      if (pluralMappers.isEmpty())
      {
        return null;
      }

      return new PluralAttributeMapper(scimAttributeType, pluralMappers);
    }
    else
    {
      return null;
    }
  }
}