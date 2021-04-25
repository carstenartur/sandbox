/*******************************************************************************
 * Copyright (c) 2020 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

public class FieldOrJavaVariable {

	public FieldOrJavaVariable(String variablename, Class<?> variabletype, int numberofusages) {
		this.variablename = variablename;
		this.variabletype = variabletype;
		this.numberofusages = numberofusages;
	}
	public String getVariablename() {
		return variablename;
	}
	public void setVariablename(String variablename) {
		this.variablename = variablename;
	}
	public Class<?> getVariabletype() {
		return variabletype;
	}
	public void setVariabletype(Class<?> variabletype) {
		this.variabletype = variabletype;
	}
	public int getNumberofusages() {
		return numberofusages;
	}
	public void setNumberofusages(int numberofusages) {
		this.numberofusages = numberofusages;
	}
	String variablename;
	Class<?> variabletype;
	int numberofusages;
}
