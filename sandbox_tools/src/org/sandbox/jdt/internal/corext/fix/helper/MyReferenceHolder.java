package org.sandbox.jdt.internal.corext.fix.helper;

import org.sandbox.jdt.internal.common.ReferenceHolder;

public class MyReferenceHolder extends ReferenceHolder<Hit> {

	public Hit possibleHit(String string) {
		Hit hit=new Hit();
		put(string, hit);
		return hit;		
	}

}
