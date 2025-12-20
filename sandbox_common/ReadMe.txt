Create helper methods based on input file using awk

1)
awk '{print "public BiFunction<? extends ASTNode,E,Boolean> add" $0 "(BiFunction<" $0 ",E,Boolean> bs) {\n		return suppliermap.put(Visitor." $0 ", bs);\n	}" "\n"}' <input.txt >output.txt

2)
awk '{print "@Override	public void endVisit(" $0 " node) {	if (consumermap.containsKey(Visitor." $0 ")){((BiConsumer<" $0 ", E>) (consumermap.get(Visitor." $0 "))).accept(node, dataholder);}}\n"}' <input.txt >output.txt

3)
awk '{print "public BiConsumer<? extends ASTNode,E> add" $0 "(BiConsumer<" $0 ",E> bc) {\n		return consumermap.put(Visitor." $0 ", bc);\n	}" "\n"}' <input.txt >output.txt

4)
awk '{print "public void add" $0 "(BiFunction<" $0 ",E,Boolean> bs,BiConsumer<" $0 ",E> bc) {\n		suppliermap.put(VisitorEnum." $0 ", bs);\nconsumermap.put(VisitorEnum." $0 ", bc);	}" "\n"}' <input.txt >output.txt

The output.txt needs to be formated then but that is easy.
