package com.spud.rpic.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Spud
 * @date 2024/10/13
 */
@Data
@ConfigurationProperties(prefix = "rpic")
public class RpicProperties {
    
}
