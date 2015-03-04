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

import org.bytesoft.utils.CommonUtils;

public class CompensableInfo implements Serializable {
	private static final long serialVersionUID = 1L;

	private String branchQualifier;
	private Serializable identifier;

	public String getBranchQualifier() {
		return branchQualifier;
	}

	public void setBranchQualifier(String branchQualifier) {
		this.branchQualifier = branchQualifier;
	}

	public Serializable getIdentifier() {
		return identifier;
	}

	public void setIdentifier(Serializable identifier) {
		this.identifier = identifier;
	}

	@Override
	public int hashCode() {
		int hash = 31;
		hash += 13 * (this.branchQualifier == null ? 0 : this.branchQualifier.hashCode());
		hash += 17 * (this.identifier == null ? 0 : this.identifier.hashCode());
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		} else if (CompensableInfo.class.isInstance(obj) == false) {
			return false;
		}
		CompensableInfo that = (CompensableInfo) obj;
		boolean qualifierEquals = CommonUtils.equals(this.branchQualifier, that.branchQualifier);
		boolean identifierEquals = CommonUtils.equals(this.identifier, that.identifier);
		return qualifierEquals && identifierEquals;
	}

}
