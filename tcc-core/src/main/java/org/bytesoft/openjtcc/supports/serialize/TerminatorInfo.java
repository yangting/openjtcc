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
package org.bytesoft.openjtcc.supports.serialize;

import java.io.Serializable;

import org.bytesoft.openjtcc.xa.XidImpl;
import org.bytesoft.utils.CommonUtils;

public class TerminatorInfo implements Serializable {
	private static final long serialVersionUID = 1L;

	private String application;
	private String endpoint;
	private XidImpl branchXid;

	public String getApplication() {
		return application;
	}

	public void setApplication(String application) {
		this.application = application;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public XidImpl getBranchXid() {
		return branchXid;
	}

	public void setBranchXid(XidImpl branchXid) {
		this.branchXid = branchXid;
	}

	@Override
	public int hashCode() {
		int hash = 31;
		hash += (this.application == null) ? 13 : this.application.hashCode();
		hash += (this.endpoint == null) ? 17 : this.endpoint.hashCode();
		hash += (this.branchXid == null) ? 19 : this.branchXid.hashCode();
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		} else if (TerminatorInfo.class.isInstance(obj) == false) {
			return false;
		}
		TerminatorInfo that = (TerminatorInfo) obj;
		boolean appEquals = CommonUtils.equals(this.application, that.application);
		boolean endEquals = CommonUtils.equals(this.endpoint, that.endpoint);
		boolean xidEquals = CommonUtils.equals(this.branchXid, that.branchXid);
		return appEquals && endEquals && xidEquals;
	}
}
