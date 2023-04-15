import java.util.Stack;
class CodegenImp implements Codegen
{

  public int labelCtr = 0;
  Stack<String> last_called_break_point = new Stack<>();
  Stack<String> last_called_loop_enter = new Stack<>();
  
  @Override
  public String codegen(Program p) throws CodegenException {
    
    String prog = ".globl enter_program \n";
    prog += ".align 2 \n\n";
    prog += "enter_program: \n";
    prog += "jal " + p.decls.get(0).id + " \n";
    prog += "b exit_program \n";
    
    for(Declaration d : p.decls) 
    {
      prog += genDecl(d);
    }
    
    prog += "exit_program: \n";
    prog += "addi zero, zero, 1 \n";
    return prog;
  }
  
  public String generate_expression(Exp thisExpression)
  {
    
    if (thisExpression instanceof IntLiteral)
    {
      return "li a0 " + ((IntLiteral) thisExpression).n + " \n";
    }
    if (thisExpression instanceof Binexp )
    {
      Binop binop = ((Binexp) thisExpression).binop;
      String this_return_string = "";

        this_return_string += generate_expression(((Binexp) thisExpression).l); 
        this_return_string += "sw a0 0(sp) \n"; 
        this_return_string += "addi sp,sp,-4 \n"; 
        this_return_string += generate_expression(((Binexp) thisExpression).r);
        this_return_string += "lw t1 4(sp) \n"; 
        this_return_string += "addi sp,sp,4 \n"; 
        this_return_string += get_math_operator(binop) + " a0 t1 a0 \n";
        return this_return_string; 
      
    }
    else if (thisExpression instanceof Invoke)
    {
      String this_return_string = "";
      String name = ((Invoke) thisExpression).name;
      
      this_return_string += "sw s0 0(sp) \n"; 
      this_return_string += "addi sp,sp,-4 \n";
      
      for (int i = ((Invoke) thisExpression).args.size() - 1; i >= 0; i--)
      {
        this_return_string += generate_expression(((Invoke) thisExpression).args.get(i));
        this_return_string += "sw a0 0(sp) \n";
        this_return_string += "addi sp,sp,-4 \n";
      }
    
      this_return_string += "jal " + name + " \n"; 
      
      return this_return_string;
    }
    else if (thisExpression instanceof Skip)
    {
      return "nop \n"; 
    }
    else if (thisExpression instanceof Assign)
    {
      String this_return_string = "";
      Assign a = (Assign)thisExpression;
      int offset = a.x * 4;
      this_return_string += generate_expression(a.e);
      this_return_string += "sw a0 " + offset +"(s0) \n";
      return this_return_string;
      
    }
    else if (thisExpression instanceof Variable)
    {
      int offset = ((Variable) thisExpression).x * 4;
      return "lw a0 " + offset + "(s0) \n";
    }
    else if (thisExpression instanceof While)
    {
      While w = (While)thisExpression;
      String bodyLabel = generate_new_label() + "_body";
      String this_return_string = "";
      
      String loopBack = generate_new_label() + "_loopback";
      String exitLabel = generate_new_label() + "_exit";
      
      last_called_loop_enter.push(loopBack);
      last_called_break_point.push(exitLabel);
        
      this_return_string += loopBack + ": \n";
      this_return_string += generate_expression(w.r);
      this_return_string += "sw a0 0(sp) \n";
      this_return_string += "addi sp,sp,-4 \n";
      this_return_string += generate_expression(w.l);
      this_return_string += "lw t1 4(sp) \n";
      this_return_string += "addi sp,sp,4 \n";
      this_return_string += getComparisonCommand(w.comp) + " a0 t1 " + bodyLabel + " \n";
        
      this_return_string += "b " + exitLabel + " \n";
      this_return_string += bodyLabel + ": " + generate_expression(w.body);
      this_return_string += "b " + loopBack + " \n";
      this_return_string += exitLabel + " : \n";
      
      last_called_break_point.pop();
      last_called_loop_enter.pop();
     
      return this_return_string;
      
    }
    else if (thisExpression instanceof Continue)
    {
      return "b " + last_called_loop_enter.firstElement() + " \n";
    }
    else if (thisExpression instanceof Break)
    {
      return "b " + last_called_break_point.firstElement() + " \n";
    }
    else if (thisExpression instanceof Assign)
    {
      Assign a = (Assign)thisExpression;
      int offset = a.x;
      String this_return_string = "";
      
      this_return_string += generate_expression(a.e);
      this_return_string += "sw a0 " + offset + "(s0)";
      
      return this_return_string;
    }
    else if (thisExpression instanceof If)
    {
    
      String this_return_string = "";
            
      String thenBranch = generate_new_label();
      String exitLabel = generate_new_label();
    
      this_return_string += generate_expression(((If) thisExpression).r);
      this_return_string += "sw a0 0(sp) \n";
      this_return_string += "addi sp,sp,-4 \n";
      this_return_string += generate_expression(((If) thisExpression).l);
      this_return_string += "lw t1 4(sp) \n";
      this_return_string += "addi sp,sp,4 \n";
      this_return_string += getComparisonCommand(((If) thisExpression).comp) + " a0 t1 " + thenBranch + " \n";
      this_return_string += generate_expression(((If) thisExpression).elseBody);
      this_return_string += "b " + exitLabel  + " \n";
      this_return_string += thenBranch + ": \n";
      this_return_string += generate_expression(((If) thisExpression).thenBody);
      this_return_string += exitLabel + " : \n";
      
      return this_return_string;
    }
    else if (thisExpression instanceof Seq)
    {
    
      return generate_expression(((Seq) thisExpression).l) + generate_expression(((Seq) thisExpression).r);
      
      
    }
    else if (thisExpression instanceof RepeatUntil)
    {
      RepeatUntil repeat_until_point = (RepeatUntil)thisExpression;
      String execute = generate_new_label() + "_body";
      String this_return_string = "";
      String loop_back_point = generate_new_label();
      String exitLabel = generate_new_label();
      
      last_called_loop_enter.push(loop_back_point);
      last_called_break_point.push(exitLabel);
        
      this_return_string += loop_back_point + ": \n";     
      this_return_string += generate_expression(repeat_until_point.body);
      
      this_return_string += generate_expression(repeat_until_point.r);
      this_return_string += "sw a0 0(sp) \n";
      this_return_string += "addi sp,sp,-4 \n";
      this_return_string += generate_expression(repeat_until_point.l);
      this_return_string += "lw t1 4(sp) \n";
      this_return_string += "addi sp,sp,4 \n";
      
      this_return_string += getComparisonCommand(repeat_until_point.comp) + " a0 t1 " + loop_back_point + " \n";    
      
      return this_return_string;
    }
   
    return "";
  }
  
  public String genDecl(Declaration d)
  {
    
    
    int sizeOfAr = (2 + d.numOfArgs) * 4;
    String this_return_string = "";
    
    this_return_string += d.id + ": \n";
    this_return_string += "mv s0 sp \n";
    this_return_string += "sw ra 0(sp) \n"; 
    this_return_string += "addi sp,sp,-4 \n";
    this_return_string += generate_expression(d.body);
    this_return_string += "lw ra 4(sp) \n"; 
    this_return_string += "addi sp,sp," + sizeOfAr + " \n";
    this_return_string += "lw s0 0(sp) \n"; 
    this_return_string += "jr ra \n";
    
    return this_return_string;
    
  }
  
 
  private String get_math_operator(Binop binop)
  {
    if (binop instanceof Plus)
    {
      return "add";
    }
    else if (binop instanceof Minus)
    {
      return "sub";
    }
    else if (binop instanceof Times)
    {
      return "mul";
    }
    else if (binop instanceof Div)
    {
      return "div";
    }
    return null;
  }
  
  private String getComparisonCommand(Comp c)
  {
    if (c instanceof Equals)
    {
      return "beq";
    }
    else if(c instanceof Less)
    {
      return "blt";
    }
    else if (c instanceof LessEq)
    {
      return "ble";
    }
    else if (c instanceof Greater)
    {
      return "bgt";
    }
    else if (c instanceof GreaterEq)
    {
      return "bge";
    }
    return null;
  }
  
  public String generate_new_label()
  {
    labelCtr ++;
    return "new_generated_label_" + labelCtr;
  }
  
  
  
}