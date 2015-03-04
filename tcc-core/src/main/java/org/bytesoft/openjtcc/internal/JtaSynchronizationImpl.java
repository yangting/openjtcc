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
package org.bytesoft.openjtcc.internal;

import javax.transaction.Synchronization;

import org.bytesoft.openjtcc.supports.AbstractSynchronization;
import org.bytesoft.openjtcc.xa.XidImpl;

public class JtaSynchronizationImpl extends AbstractSynchronization {
	public Synchronization synchronization;

	public JtaSynchronizationImpl(XidImpl globalXid, Synchronization sync) {
		super(globalXid);
		this.synchronization = sync;
	}

	public void afterCreation(XidImpl xid) {
		// ignore
	}

	public void beforeCompletion(XidImpl xid) {
		if (this.synchronization != null) {
			this.synchronization.beforeCompletion();
		}
	}

	public void afterCompletion(XidImpl xid, int status) {
		if (this.synchronization != null) {
			this.synchronization.afterCompletion(status);
		}
	}
}
