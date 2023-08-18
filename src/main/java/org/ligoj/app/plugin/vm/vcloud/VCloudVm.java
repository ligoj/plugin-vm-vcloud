/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.vm.vcloud;

import org.ligoj.app.plugin.vm.execution.Vm;

import lombok.Getter;
import lombok.Setter;

/**
 * vCloud Virtual machine description.
 */
@Getter
@Setter
public class VCloudVm extends Vm {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

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
