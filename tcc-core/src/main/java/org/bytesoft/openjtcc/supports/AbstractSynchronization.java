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
package org.bytesoft.openjtcc.supports;

import javax.transaction.Synchronization;

import org.bytesoft.openjtcc.xa.XidImpl;

public abstract class AbstractSynchronization implements Synchronization {

	private XidImpl xid;
	private boolean beforeCompletionRequired;
	private boolean afterCompletionRequired;

	public AbstractSynchronization() {
		this(null);
	}

	public AbstractSynchronization(XidImpl xid) {
		this.xid = xid;
		this.beforeCompletionRequired = true;
		this.afterCompletionRequired = true;
	}

	public final void beforeCompletion() {
		if (this.beforeCompletionRequired) {
			this.beforeCompletionRequired = false;
			this.beforeCompletion(this.xid);
		}
	}

	public final void afterCompletion(int status) {
		if (this.afterCompletionRequired) {
			this.afterCompletionRequired = false;
			this.afterCompletion(this.xid, status);
		}
	}

	public abstract void afterCreation(XidImpl xid);

	public abstract void beforeCompletion(XidImpl xid);

	public abstract void afterCompletion(XidImpl xid, int status);

	public void setXid(XidImpl xid) {
		this.xid = xid;
	}

}
