/**
 * ESDLBindingContents.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4;

public class ESDLBindingContents  implements java.io.Serializable {
    private org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.ESDLDefinition definition;

    private org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.ESDLConfiguration configuration;

    private org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.PublishHistory history;

    public ESDLBindingContents() {
    }

    public ESDLBindingContents(
           org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.ESDLDefinition definition,
           org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.ESDLConfiguration configuration,
           org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.PublishHistory history) {
           this.definition = definition;
           this.configuration = configuration;
           this.history = history;
    }


    /**
     * Gets the definition value for this ESDLBindingContents.
     * 
     * @return definition
     */
    public org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.ESDLDefinition getDefinition() {
        return definition;
    }


    /**
     * Sets the definition value for this ESDLBindingContents.
     * 
     * @param definition
     */
    public void setDefinition(org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.ESDLDefinition definition) {
        this.definition = definition;
    }


    /**
     * Gets the configuration value for this ESDLBindingContents.
     * 
     * @return configuration
     */
    public org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.ESDLConfiguration getConfiguration() {
        return configuration;
    }


    /**
     * Sets the configuration value for this ESDLBindingContents.
     * 
     * @param configuration
     */
    public void setConfiguration(org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.ESDLConfiguration configuration) {
        this.configuration = configuration;
    }


    /**
     * Gets the history value for this ESDLBindingContents.
     * 
     * @return history
     */
    public org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.PublishHistory getHistory() {
        return history;
    }


    /**
     * Sets the history value for this ESDLBindingContents.
     * 
     * @param history
     */
    public void setHistory(org.hpccsystems.ws.client.gen.wsesdlconfig.v1_4.PublishHistory history) {
        this.history = history;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof ESDLBindingContents)) return false;
        ESDLBindingContents other = (ESDLBindingContents) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.definition==null && other.getDefinition()==null) || 
             (this.definition!=null &&
              this.definition.equals(other.getDefinition()))) &&
            ((this.configuration==null && other.getConfiguration()==null) || 
             (this.configuration!=null &&
              this.configuration.equals(other.getConfiguration()))) &&
            ((this.history==null && other.getHistory()==null) || 
             (this.history!=null &&
              this.history.equals(other.getHistory())));
        __equalsCalc = null;
        return _equals;
    }

    private boolean __hashCodeCalc = false;
    public synchronized int hashCode() {
        if (__hashCodeCalc) {
            return 0;
        }
        __hashCodeCalc = true;
        int _hashCode = 1;
        if (getDefinition() != null) {
            _hashCode += getDefinition().hashCode();
        }
        if (getConfiguration() != null) {
            _hashCode += getConfiguration().hashCode();
        }
        if (getHistory() != null) {
            _hashCode += getHistory().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(ESDLBindingContents.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:hpccsystems:ws:wsesdlconfig", "ESDLBindingContents"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("definition");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:hpccsystems:ws:wsesdlconfig", "Definition"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:hpccsystems:ws:wsesdlconfig", "ESDLDefinition"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("configuration");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:hpccsystems:ws:wsesdlconfig", "Configuration"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:hpccsystems:ws:wsesdlconfig", "ESDLConfiguration"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("history");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:hpccsystems:ws:wsesdlconfig", "History"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:hpccsystems:ws:wsesdlconfig", "PublishHistory"));
        elemField.setMinOccurs(0);
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
    }

    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

    /**
     * Get Custom Serializer
     */
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanSerializer(
            _javaType, _xmlType, typeDesc);
    }

    /**
     * Get Custom Deserializer
     */
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanDeserializer(
            _javaType, _xmlType, typeDesc);
    }

}
