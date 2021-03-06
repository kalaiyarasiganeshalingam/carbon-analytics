package org.wso2.carbon.event.simulator.core.internal.generator.random;

import org.json.JSONObject;
import org.wso2.carbon.event.simulator.core.exception.InvalidConfigException;

/**
 * RandomAttributeGenerator interface defines common methods used by all random attribute generators
 * This interface is implemented by
 *
 * @see org.wso2.carbon.event.simulator.core.internal.generator.random.util.CustomBasedAttrGenerator
 * @see org.wso2.carbon.event.simulator.core.internal.generator.random.util.PrimitiveBasedAttrGenerator
 * @see org.wso2.carbon.event.simulator.core.internal.generator.random.util.PropertyBasedAttrGenerator
 * @see org.wso2.carbon.event.simulator.core.internal.generator.random.util.RegexBasedAttrGenerator
 */
public interface RandomAttributeGenerator {

    Object generateAttribute();

    String getAttributeConfiguration();

    void validateAttributeConfiguration(JSONObject attributeConfig) throws InvalidConfigException;

    void createRandomAttributeDTO(JSONObject attributeConfig);

    /**
     * enum RandomDataGeneratorType specifies the random simulation types supported
     **/
    enum RandomDataGeneratorType {
        PRIMITIVE_BASED, PROPERTY_BASED, REGEX_BASED, CUSTOM_DATA_BASED
    }

}
