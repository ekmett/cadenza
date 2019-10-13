grammar Core;

@header{
package core.syntax;
}

expression
    : VARIABLE | function | application
    ;

lambda
    : 'λ' | '\\'
    ;

function
    : lambda VARIABLE '.' scope
    ;

application
    : '(' expression expression ')'
    ;

scope
    : expression
    ;


VARIABLE
    : [a-z] [a-zA-Z0-9]*
    ;

WS
   : [ \t\r\n] -> skip
   ;
