function getAddress(inputViewName, address)
{
    var fields = 
    [ 
        { name: "name" },
        { name: "street" },
        { name: "city" },
        { name: "zip" },
        { name: "state", optional: true },
        { name: "country" }
    ];

    for(;;)
    {
        respondAndWait(inputViewName, address);
        address = {};
        var inputOkay = true;
        for(var i in fields)
        {
            var field = fields[i];
            var fieldName = field.name;
            var fieldValue = request.getParameter(fieldName);
            if(!field.optional && (fieldValue == null || fieldValue == ""))
            {
                if(address.errors == null)
                {
                    address.errors = {};
                }
                address.errors[fieldName] = "requiredField";
            }
            address[fieldName] = fieldValue;
        }
        if(address.errors == null)
        {
            return address;
        }
    }
}

var addresses = {};
addresses.shippingAddress = getAddress("index", {});
addresses.billingAddress  = getAddress("billingAddress", addresses.shippingAddress);
respondAndWait("confirm", addresses);
respond("thankyou");