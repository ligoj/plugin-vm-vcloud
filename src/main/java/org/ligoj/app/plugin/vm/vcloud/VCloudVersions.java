/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.vm.vcloud;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * vCloud version structure.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Setter
@Getter
public class VCloudVersions {

	protected List<Map<String, String>> versions;

}
