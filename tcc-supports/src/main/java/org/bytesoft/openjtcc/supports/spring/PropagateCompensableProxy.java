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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bytesoft.openjtcc.TransactionManagerImpl;

public class PropagateCompensableProxy<T extends Serializable> extends NativeCompensableProxy<T> {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected Object invokeBizMethod(Method method, Object[] args) throws Throwable {
		PropagateCompensableSynchronization sync = new PropagateCompensableSynchronization(this.facade);
		sync.setTransactionManager(this.transactionManager);
		TransactionManagerImpl txManager = (TransactionManagerImpl) this.transactionManager;
		try {
			txManager.registerSynchronization(sync);

			return method.invoke(this.target, args);
		} catch (IllegalAccessException ex) {
			RuntimeException rex = new RuntimeException();
			rex.initCause(ex);
			throw rex;
		} catch (InvocationTargetException ex) {
			throw ex.getTargetException();
		} finally {
			txManager.unRegisterSynchronization(sync);
		}
	}

	public int hashCode() {
		return (int) this.proxyId;
	}

	@SuppressWarnings("unchecked")
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		} else if (!PropagateCompensableProxy.class.equals(obj.getClass())) {
			return false;
		}
		PropagateCompensableProxy<T> that = (PropagateCompensableProxy<T>) obj;
		return this.proxyId == that.proxyId;
	}
}
