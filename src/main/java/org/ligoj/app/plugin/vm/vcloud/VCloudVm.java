package org.ligoj.app.plugin.vm.vcloud;

import org.ligoj.app.plugin.vm.Vm;

import lombok.Getter;
import lombok.Setter;

/**
 * vCloud Virtual machine description.
 */
@Getter
@Setter
public class VCloudVm extends Vm {

	private String storageProfileName;

	/**
	 * vApp name.
	 */
	private String vApp;

	/**
	 * vAPP identifier.
	 */
	private String vAppId;
}
