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
package org.bytesoft.openjtcc.common;

import java.io.Serializable;

import org.bytesoft.utils.CommonUtils;

public class TerminalKey implements Cloneable, Serializable {
	private static final long serialVersionUID = 1L;

	private String application = "unspecified";
	private String endpoint = "unspecified";

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

	public TerminalKey clone() throws CloneNotSupportedException {
		TerminalKey that = new TerminalKey();
		that.setApplication(this.application);
		that.setEndpoint(this.endpoint);
		return that;
	}

	public int hashCode() {
		int hash = 23;
		hash += 29 * (this.application == null ? 0 : this.application.hashCode());
		hash += 31 * (this.endpoint == null ? 0 : this.endpoint.hashCode());
		return hash;
	}

	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		} else if (TerminalKey.class.equals(obj.getClass()) == false) {
			return false;
		}

		TerminalKey that = (TerminalKey) obj;
		boolean appEquals = CommonUtils.equals(this.application, that.application);
		boolean endEquals = CommonUtils.equals(this.endpoint, that.endpoint);
		return appEquals && endEquals;
	}

	public String toString() {
		return String.format("terminal: application= %s, endpoint= %s", this.application, this.endpoint);
	}

}
