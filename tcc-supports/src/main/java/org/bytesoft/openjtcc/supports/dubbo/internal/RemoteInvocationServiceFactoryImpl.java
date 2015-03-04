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

import java.lang.reflect.Proxy;

import org.bytesoft.openjtcc.common.TerminalKey;
import org.bytesoft.openjtcc.supports.dubbo.RemoteInvocationService;
import org.bytesoft.openjtcc.supports.dubbo.RemoteInvocationServiceFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class RemoteInvocationServiceFactoryImpl implements RemoteInvocationServiceFactory, ApplicationContextAware {
	private ApplicationContext applicationContext;

	@Override
	public RemoteInvocationService getRemoteInvocationService(TerminalKey terminalKey) {
		RemoteInvocationEndpointSelector handler = new RemoteInvocationEndpointSelector();
		handler.setTerminalKey(terminalKey);
		handler.setApplicationContext(this.applicationContext);
		return (RemoteInvocationService) Proxy.newProxyInstance(RemoteInvocationService.class.getClassLoader(),
				new Class[] { RemoteInvocationService.class }, handler);
	}

	@Override
	public RemoteInvocationService getRemoteInvocationService() {
		RemoteInvocationEndpointSelector handler = new RemoteInvocationEndpointSelector();
		handler.setApplicationContext(this.applicationContext);
		return (RemoteInvocationService) Proxy.newProxyInstance(RemoteInvocationService.class.getClassLoader(),
				new Class[] { RemoteInvocationService.class }, handler);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

}
