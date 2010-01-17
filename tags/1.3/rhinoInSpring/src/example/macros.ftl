[-- In real life example, this would be a localized resource --]
[#assign errorMsg = { "requiredField" : "This field is required" }/]

[#macro field fieldDescription, fieldName]
  <tr>
    <td>${fieldDescription}: </td>
    <td><input type="text" size="20" name="${fieldName}" value="${.vars[fieldName]?default("")}"></td>
    <td>
      [#if errors?exists && errors[fieldName]?exists]
        <font color="red">${errorMsg[errors[fieldName]]}</font>
      [#else]
        &nbsp;
      [/#if]
    </td>
  </tr>
[/#macro]

