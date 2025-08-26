/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.vm.vcloud;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.curl.DefaultHttpResponseCallback;

import java.io.IOException;

/**
 * vCloud login response handler saving the "x-vcloud-authorization".
 */
public class VCloudLoginHttpResponseCallback extends DefaultHttpResponseCallback {

	@Override
	public boolean onResponse(final CurlRequest request, final ClassicHttpResponse response) throws IOException {
		final var result = super.onResponse(request, response);
		if (result) {
			// Success request, get the authentication token
			final var token = ObjectUtils
					.getIfNull(response.getFirstHeader("x-vcloud-authorization"), new BasicHeader("", null))
					.getValue();

			// Save this token in the associated processor for next requests
			((VCloudCurlProcessor) request.getProcessor()).setToken(token);
		}
		return result;
	}
}
