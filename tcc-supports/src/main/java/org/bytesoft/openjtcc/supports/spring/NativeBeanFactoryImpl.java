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

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;

import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.bytesoft.openjtcc.Compensable;
import org.bytesoft.openjtcc.supports.NativeBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class NativeBeanFactoryImpl implements NativeBeanFactory, ApplicationContextAware {
	private ApplicationContext applicationContext;
	private TransactionManager transactionManager;

	private final AtomicLong atomic = new AtomicLong();

	@SuppressWarnings("unchecked")
	public <T> T getBean(Class<T> interfaceClass, String beanId) {
		Object beanInst = this.applicationContext.getBean(beanId);
		if (interfaceClass.isInstance(beanInst) //
				&& Compensable.class.isInstance(beanInst)//
		) {
			if (this.transactionManager == null) {
				throw new RuntimeException();
			}

			try {
				if (this.transactionManager.getTransaction() != null) {
					throw new RuntimeException();
				}
			} catch (SystemException ex) {
				throw new RuntimeException(ex);
			}

			NativeCompensableProxy<Serializable> proxy = new NativeCompensableProxy<Serializable>();
			proxy.setBeanName(beanId);
			proxy.setTarget((Compensable<Serializable>) beanInst);
			proxy.setProxyId(atomic.incrementAndGet());
			proxy.setTransactionManager(this.transactionManager);

			ClassLoader classLoader = beanInst.getClass().getClassLoader();
			Class<?>[] interfaces = null;
			if (Compensable.class.equals(interfaceClass)) {
				interfaces = new Class[] { interfaceClass };
			} else {
				interfaces = new Class[] { interfaceClass, Compensable.class };
			}
			Object proxyInst = Proxy.newProxyInstance(classLoader, interfaces, proxy);

			proxy.setFacade((Compensable<Serializable>) proxyInst);

			return interfaceClass.cast(proxyInst);
		}
		throw new RuntimeException();
	}

	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	public void setTransactionManager(TransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

}
