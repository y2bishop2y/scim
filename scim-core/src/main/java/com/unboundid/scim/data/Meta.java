/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */
package com.unboundid.scim.data;

import com.unboundid.scim.schema.AttributeDescriptor;
import com.unboundid.scim.sdk.InvalidResourceException;
import com.unboundid.scim.sdk.SCIMAttribute;
import com.unboundid.scim.sdk.SCIMAttributeValue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

/**
 * A complex type containing metadata about the resource.
 */
public class Meta
{
  /**
   * The <code>AttributeValueResolver</code> that resolves SCIM attribute values
   * to/from <code>Meta</code> instances.
   */
  public static final AttributeValueResolver<Meta> META_RESOLVER =
      new AttributeValueResolver<Meta>() {
        /**
         * {@inheritDoc}
         */
        @Override
        public Meta toInstance(final SCIMAttributeValue value) {
          return new Meta(
              value.getSingularSubAttributeValue("created",
                  DATE_RESOLVER),
              value.getSingularSubAttributeValue("lastModified",
                  DATE_RESOLVER),
              URI.create(value.getSingularSubAttributeValue("location",
                  STRING_RESOLVER)),
              value.getSingularSubAttributeValue("version",
                  STRING_RESOLVER));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SCIMAttributeValue fromInstance(
            final AttributeDescriptor attributeDescriptor,
            final Meta value) throws InvalidResourceException {
          Collection<SCIMAttribute> attributes =
              new ArrayList<SCIMAttribute>(3);

          if(value.created != null)
          {
            AttributeDescriptor subAttributeDescriptor =
                attributeDescriptor.getSubAttribute("created");
            attributes.add(SCIMAttribute.createSingularAttribute(
                subAttributeDescriptor,
                SCIMAttributeValue.createDateValue(value.created)));
          }
          if(value.lastModified != null)
          {
            AttributeDescriptor subAttributeDescriptor =
                attributeDescriptor.getSubAttribute("lastModified");
            attributes.add(SCIMAttribute.createSingularAttribute(
                subAttributeDescriptor,
                SCIMAttributeValue.createDateValue(value.lastModified)));
          }
          if(value.location != null)
          {
            AttributeDescriptor subAttributeDescriptor =
                attributeDescriptor.getSubAttribute("location");
            attributes.add(SCIMAttribute.createSingularAttribute(
                subAttributeDescriptor,
                SCIMAttributeValue.createStringValue(
                    value.location.toString())));
          }
          if(value.version != null)
          {
            AttributeDescriptor subAttributeDescriptor =
                attributeDescriptor.getSubAttribute("version");
            attributes.add(SCIMAttribute.createSingularAttribute(
                subAttributeDescriptor,
                SCIMAttributeValue.createStringValue(value.version)));
          }

          return SCIMAttributeValue.createComplexValue(attributes);
        }
      };

  private Date created;
  private Date lastModified;
  private URI location;
  private String version;

  /**
   * Create an instance of the SCIM meta attribute.
   *
   * @param created         The time the Resource was added to the
   *                        Service Provider.
   * @param lastModified    The most recent time the details of a Resource
   *                        were updated at the Service Provider.
   * @param location        The URI of the Resource.
   * @param version         The version of the Resource.
   */
  public Meta(final Date created, final Date lastModified, final URI location,
              final String version) {
    this.created = created;
    this.lastModified = lastModified;
    this.location = location;
    this.version = version;
  }

  /**
   * Retrieves the time the Resource was added to the Service Provider.
   *
   * @return The time the Resource was added to the Service Provider.
   */
  public Date getCreated() {
    return created;
  }

  /**
   * Sets the time the Resource was added to the Service Provider.
   *
   * @param created The time the Resource was added to the Service Provider.
   */
  public void setCreated(final Date created) {
    this.created = created;
  }

  /**
   * Retrieves the most recent time the details of a Resource were updated at
   * the Service Provider.
   *
   * @return The most recent time the details of a Resource were updated at
   * the Service Provider.
   */
  public Date getLastModified() {
    return lastModified;
  }

  /**
   * Sets the most recent time the details of a Resource were updated at
   * the Service Provider.
   *
   * @param lastModified The most recent time the details of a Resource were
   * updated at the Service Provider.
   */
  public void setLastModified(final Date lastModified) {
    this.lastModified = lastModified;
  }

  /**
   * Retrieves the URI of the Resource.
   *
   * @return The URI of the Resource.
   */
  public URI getLocation() {
    return location;
  }

  /**
   * Sets the URI of the Resource.
   *
   * @param location The URI of the Resource.
   */
  public void setLocation(final URI location) {
    this.location = location;
  }

  /**
   * Retrieves the version of the Resource.
   *
   * @return The version of the Resource.
   */
  public String getVersion() {
    return version;
  }

  /**
   * Sets the version of the Resource being returned.
   *
   * @param version The version of the Resource being returned.
   */
  public void setVersion(final String version) {
    this.version = version;
  }
}