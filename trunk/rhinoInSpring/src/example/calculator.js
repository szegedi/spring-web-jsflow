var tape = new Array();
tape[0] = 0;
for(;;)
{
    respondAndWait("calculator", { tape: tape });
    var operand1 = tape[tape.length - 1];
    try
    {
        var operation = 
            request.getParameter("operator") + " " + 
            request.getParameter("operand");
           
        tape[tape.length] = "&nbsp;&nbsp;" + operation;
        tape[tape.length] = eval(operand1 + " " + operation);
    }
    catch(e)
    {
        tape[tape.length] = "&nbsp;&nbsp;Error: " + e.message;
        tape[tape.length] = operand1;
    }
}
