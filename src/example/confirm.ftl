<html>
  <head>
    <title>Rhino books - Checkout - Confirm</title>
  </head>
  <body>
    <center>
    <h1>Rhino books - Checkout - Confirm</h1>
    <p>Thank you for shopping at Rhino books. To complete your order, 
    please fill out the information on the next few pages. If you need 
    to go back, just use your browser's back button.</p>
    <h2>Confirm addresses</h2>
    <p>Please confirm your shipping and billing address</p>
    <form method="POST" action="checkout.js">
      <input type="hidden" name="stateId" value="${stateId}">
      <h3>Shipping address</h3>
      <table border="0">
        <tr><td>Name: </td><td>${shippingAddress.name}</td></tr>
        <tr><td>Street address:</td><td>${shippingAddress.street}</td></tr>
        <tr><td>City:</td><td>${shippingAddress.city}</td></tr>
        <tr><td>Zip code:</td><td>${shippingAddress.zip}</td></tr>
        <tr><td>State:</td><td>${shippingAddress.state}</td></tr>
        <tr><td>Country:</td><td>${shippingAddress.country}</td></tr>
      </table>
      <h3>Billing address</h3>
      <table border="0">
        <tr><td>Name: </td><td>${billingAddress.name}</td></tr>
        <tr><td>Street address:</td><td>${billingAddress.street}</td></tr>
        <tr><td>City:</td><td>${billingAddress.city}</td></tr>
        <tr><td>Zip code:</td><td>${billingAddress.zip}</td></tr>
        <tr><td>State:</td><td>${billingAddress.state}</td></tr>
        <tr><td>Country:</td><td>${billingAddress.country}</td></tr>
      </table>
      <input type="submit" name="" value="Finish >">
   </center>
  </body>
</html>