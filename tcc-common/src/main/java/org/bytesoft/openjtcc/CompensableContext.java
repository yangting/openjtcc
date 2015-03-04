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
package org.bytesoft.openjtcc;

import java.io.Serializable;

public interface CompensableContext<T extends Serializable> {

	public T getCompensableVariable() throws IllegalStateException;

	public void setCompensableVariable(T variable) throws IllegalStateException;

	public void setRollbackOnly() throws IllegalStateException;

	public boolean isRollbackOnly() throws IllegalStateException;

	public String getGlobalTransactionId() throws IllegalStateException;

	public boolean isCoordinator() throws IllegalStateException;

}
