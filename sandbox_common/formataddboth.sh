awk '{print "public void add" $0 "(BiFunction<" $0 ",E,Boolean> bs,BiConsumer<" $0 ",E> bc) {\n		suppliermap.put(VisitorEnum." $0 ", bs);\nconsumermap.put(VisitorEnum." $0 ", bc);	}" "\n"}' <input.txt >output.txt