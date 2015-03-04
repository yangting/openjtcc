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
package org.bytesoft.openjtcc.supports.marshall;

import java.io.Serializable;
import java.lang.reflect.Proxy;

import org.bytesoft.openjtcc.Compensable;
import org.bytesoft.openjtcc.supports.NativeBeanFactory;
import org.bytesoft.openjtcc.supports.serialize.CompensableInfo;
import org.bytesoft.openjtcc.supports.serialize.CompensableMarshaller;
import org.bytesoft.openjtcc.supports.spring.NativeCompensableProxy;

public class CompensableMarshallerImpl implements CompensableMarshaller/* , ApplicationContextAware */{
	private NativeBeanFactory beanFactory;

	@SuppressWarnings("unchecked")
	public CompensableInfo marshallCompensable(Compensable<Serializable> compensable) {
		if (Proxy.isProxyClass(compensable.getClass())) {
			NativeCompensableProxy<Serializable> handler = (NativeCompensableProxy<Serializable>) Proxy
					.getInvocationHandler(compensable);
			try {
				CompensableInfo info = new CompensableInfo();
				info.setIdentifier(handler.getBeanName());
				return info;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public Compensable<Serializable> unmarshallCompensable(CompensableInfo info) {
		Serializable identifier = info.getIdentifier();
		if (info != null && String.class.isInstance(identifier)) {
			String beanName = (String) identifier;
			Compensable<Serializable> service = this.beanFactory.getBean(Compensable.class, beanName);
			return service;
		}
		return null;
	}

	public void setBeanFactory(NativeBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

}
