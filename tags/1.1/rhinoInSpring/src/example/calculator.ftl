<html>
  <head>
    <title>Tape calculator</title>
  </head>
  <body>
    <center>
    <h1>Tape calculator</h1>
    <p>Emulates a calculator that prints each operation on a tape. Enter an 
    operation and a number. You can use the browser's back button
    to step back as far as you want and continue from there, or you can 
    duplicate browser windows to split the calculation into multiple independent
    calculations as you wish.</p>
    <hr>
    [#foreach item in tape]
    <p>${item}</p>
    [/#foreach]
    <hr>
    <form method="POST" action="calculator.js">
      <input type="hidden" name="stateId" value="${stateId}">
      <table border="0">
        <tr><td>Operation:</td><td>
          <select name="operator">
            <option>+</option>
            <option>-</option>
            <option>*</option>
            <option>/</option>
          </select></td></tr>
        <tr><td>Number:</td><td><input type="text" name="operand"></td></tr>
        <tr><td colspan="2"><input type="submit" value="Calculate"></td></tr>
      </table>
    </form>
    </center>
  </body>
</html>