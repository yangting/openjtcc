/**
 * Copyright 2014 yangming.liu<liuyangming@gmail.com>.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, see <http://www.gnu.org/licenses/>.
 */
package org.bytesoft.openjtcc.supports.dubbo.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.bytesoft.openjtcc.common.TerminalKey;
import org.bytesoft.openjtcc.supports.dubbo.RemoteInvocationService;
import org.springframework.context.ApplicationContext;

public class RemoteInvocationEndpointSelector implements InvocationHandler {
	public static final String DEFAULT_REMOTE_SERVICE_NAME = "remote-service";
	private ApplicationContext applicationContext;
	private TerminalKey terminalKey;

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Method handlerMethod = null;
		try {
			handlerMethod = RemoteInvocationEndpointSelector.class.getDeclaredMethod(method.getName(),
					method.getParameterTypes());
			return handlerMethod.invoke(this, args);
		} catch (Exception ex) {
			RemoteInvocationService delegate = this.getRemoteInvocationService();
			return method.invoke(delegate, args);
		}
	}

	private RemoteInvocationService getRemoteInvocationService() {

		String application = null;
		String endpoint = null;

		if (this.terminalKey != null) {
			application = this.terminalKey.getApplication();
			endpoint = this.terminalKey.getEndpoint();
		}

		if (application == null || application.trim().equals("")) {
			return (RemoteInvocationService) this.applicationContext.getBean(DEFAULT_REMOTE_SERVICE_NAME);
		} else {
			String beanName = null;
			if (endpoint == null || endpoint.trim().equals("")) {
				beanName = String.format("%s-%s", application, DEFAULT_REMOTE_SERVICE_NAME);
			} else {
				beanName = String.format("%s-%s-%s", application, endpoint, DEFAULT_REMOTE_SERVICE_NAME);
			}
			return (RemoteInvocationService) this.applicationContext.getBean(beanName);
		}
	}

	public ApplicationContext getApplicationContext() {
		return applicationContext;
	}

	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	public TerminalKey getTerminalKey() {
		return terminalKey;
	}

	public void setTerminalKey(TerminalKey terminalKey) {
		this.terminalKey = terminalKey;
	}

}
