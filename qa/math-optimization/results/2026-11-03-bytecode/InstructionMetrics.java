import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.*;
import java.nio.file.*;
import java.util.*;
public class InstructionMetrics {
  public static void main(String[] files) throws Exception {
    Set<String> selected = Set.of("f", "g", "F", "G", "Ch", "Maj", "addMerge", "getTextLayout");
    for (String file : files) {
      var model = ClassFile.of().parse(Path.of(file));
      for (var method : model.methods()) {
        String name = method.methodName().stringValue();
        if (!selected.contains(name) || method.code().isEmpty()) continue;
        var code = method.code().get();
        int instructions = 0, branches = 0, allocations = 0;
        Map<String, Integer> histogram = new TreeMap<>();
        List<String> calls = new ArrayList<>();
        for (var element : code) {
          if (!(element instanceof Instruction instruction)) continue;
          instructions++;
          histogram.merge(instruction.opcode().name(), 1, Integer::sum);
          if (instruction instanceof BranchInstruction || instruction instanceof LookupSwitchInstruction || instruction instanceof TableSwitchInstruction) branches++;
          if (instruction instanceof NewObjectInstruction || instruction instanceof NewPrimitiveArrayInstruction || instruction instanceof NewReferenceArrayInstruction || instruction instanceof NewMultiArrayInstruction) allocations++;
          if (instruction instanceof InvokeInstruction call) calls.add(call.owner().asInternalName()+"."+call.name().stringValue()+call.type().stringValue());
          if (instruction instanceof InvokeDynamicInstruction) calls.add("invokedynamic");
        }
        var attributes = (CodeAttribute) code;
        System.out.printf("%s\t%s%s\tinstructions=%d\tcodeBytes=%d\tbranches=%d\tallocations=%d\tmaxStack=%d\tmaxLocals=%d\thandlers=%d\tcalls=%s\thistogram=%s%n", file, name, method.methodType().stringValue(), instructions, attributes.codeLength(), branches, allocations, attributes.maxStack(), attributes.maxLocals(), code.exceptionHandlers().size(), calls, histogram);
      }
    }
  }
}
