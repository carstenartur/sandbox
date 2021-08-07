package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.HashMap;
import java.util.Map;

import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.common.VisitorEnum;

public class MyReferenceHolder extends ReferenceHolder<String, Hit> {

	Map<VisitorEnum,Object> vistor2data=new HashMap<>();

	public Hit possibleHit(String string) {
		Hit hit=new Hit();
		put(string, hit);
		return hit;		
	}

	public Object getNodeData(VisitorEnum node) {
		return vistor2data.get(node);
	}

	public void setNodeData(VisitorEnum node, Object data) {
		this.vistor2data.put(node, data);
	}
	
	@Override
	public int hashCode() {
		return super.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return super.equals(o);
	}
}
