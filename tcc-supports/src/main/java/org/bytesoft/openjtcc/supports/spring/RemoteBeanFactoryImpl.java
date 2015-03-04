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
package org.bytesoft.openjtcc.supports.spring;

import java.lang.reflect.Proxy;

import javax.transaction.TransactionManager;

import org.bytesoft.openjtcc.common.TerminalKey;
import org.bytesoft.openjtcc.supports.RemoteBeanFactory;
import org.bytesoft.openjtcc.supports.dubbo.RemoteInvocationServiceFactory;
import org.bytesoft.openjtcc.supports.dubbo.internal.RemoteInvocationClientInvoker;
import org.bytesoft.openjtcc.supports.rmi.RemoteInvocationInterceptor;

public class RemoteBeanFactoryImpl implements RemoteBeanFactory {

	private String application;
	private TransactionManager transactionManager;
	private RemoteInvocationInterceptor remoteInvocationInterceptor;
	private RemoteInvocationServiceFactory remoteInvocationServiceFactory;

	@Override
	public <T> T getBean(Class<T> interfaceClass, String beanId) {
		RemoteInvocationClientInvoker client = new RemoteInvocationClientInvoker();
		client.setRemoteInvocationServiceFactory(this.remoteInvocationServiceFactory);
		client.setTransactionManager(this.transactionManager);
		client.setRemoteInvocationInterceptor(this.remoteInvocationInterceptor);
		client.setRemoteInterfaceClass(interfaceClass);
		client.setBeanId(beanId);
		TerminalKey terminalKey = new TerminalKey();
		terminalKey.setApplication(this.application);
		terminalKey.setEndpoint(null);
		client.setTerminalKey(terminalKey);
		Object proxyInst = Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[] { interfaceClass },
				client);
		return interfaceClass.cast(proxyInst);
	}

	public void setTransactionManager(TransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	public void setRemoteInvocationInterceptor(RemoteInvocationInterceptor remoteInvocationInterceptor) {
		this.remoteInvocationInterceptor = remoteInvocationInterceptor;
	}

	public void setRemoteInvocationServiceFactory(RemoteInvocationServiceFactory remoteInvocationServiceFactory) {
		this.remoteInvocationServiceFactory = remoteInvocationServiceFactory;
	}

	public void setApplication(String application) {
		this.application = application;
	}

}
