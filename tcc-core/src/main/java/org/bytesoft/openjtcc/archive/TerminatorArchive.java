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
package org.bytesoft.openjtcc.archive;

import org.bytesoft.openjtcc.remote.RemoteTerminator;
import org.bytesoft.utils.CommonUtils;

public class TerminatorArchive {
	public RemoteTerminator terminator;
	public boolean prepared;
	public boolean committed;
	public boolean rolledback;
	public boolean cleanup;

	public int hashCode() {
		int hash = 23;
		hash += 29 * (this.terminator == null ? 0 : this.terminator.hashCode());
		return hash;
	}

	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		} else if (this.getClass().equals(obj.getClass()) == false) {
			return false;
		}
		TerminatorArchive that = (TerminatorArchive) obj;
		return CommonUtils.equals(this.terminator, that.terminator);
	}

}
