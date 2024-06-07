/*
 * Copyright 2008 Ayman Al-Sairafi ayman.alsairafi@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License
 *       at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xemplarsoft;


import jsyntaxpane.Token;
import jsyntaxpane.TokenType;
import jsyntaxpane.lexers.DefaultJFlexLexer;

%%

%public
%class Assembly6502Lexer
%extends DefaultJFlexLexer
%final
%unicode
%char
%type Token

%{
    /**
     * Create an empty lexer, yyrset will be called later to reset and assign
     * the reader
     */
    public Assembly6502Lexer() {
        super();
    }

    private static final byte PARAN     = 1;

    @Override
    public int yychar() {
        return (int)yychar;
    }

%}

LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]

Identifier = [a-zA-Z][a-zA-Z0-9_]*

Comment = ";"  {InputCharacter}* {LineTerminator}?

Numbers  = [(\b|\$|\%)([0-9A-Fa-f]+)\b]
IndexVal = [((,)(y|Y|x|X)+)\b]
Labels   = {InputCharacter}*  ":" {LineTerminator}?

StringCharacter = [^\r\n\"\\]
SingleCharacter = [^\r\n\'\\]


%%

<YYINITIAL>
{
  /* Bash keywords */
  "("                           { return token(TokenType.OPERATOR,  PARAN); }
  ")"                           { return token(TokenType.OPERATOR, -PARAN); }

  "adc"                         |
  "and"                         |
  "asl"                         |
  "bcc"                         |
  "bcs"                         |
  "beq"                         |
  "bit"                         |
  "bmi"                         |
  "bne"                         |
  "bpl"                         |
  "brk"                         |
  "bvc"                         |
  "bvs"                         |
  "clc"                         |
  "cld"                         |
  "cli"                         |
  "clv"                         |
  "cmp"                         |
  "cpx"                         |
  "cpy"                         |
  "dec"                         |
  "dex"                         |
  "dey"                         |
  "eor"                         |
  "inc"                         |
  "inx"                         |
  "iny"                         |
  "jmp"                         |
  "jsr"                         |
  "lda"                         |
  "ldx"                         |
  "ldy"                         |
  "lsr"                         |
  "nop"                         |
  "ora"                         |
  "pha"                         |
  "php"                         |
  "pla"                         |
  "plp"                         |
  "rol"                         |
  "ror"                         |
  "rti"                         |
  "rts"                         |
  "sbc"                         |
  "sec"                         |
  "sed"                         |
  "sei"                         |
  "sta"                         |
  "stx"                         |
  "sty"                         |
  "tax"                         |
  "tay"                         |
  "tsx"                         |
  "txa"                         |
  "txs"                         |
  "tya"                         |
  "ADC"                         |
  "AND"                         |
  "ASL"                         |
  "BCC"                         |
  "BCS"                         |
  "BEQ"                         |
  "BIT"                         |
  "BMI"                         |
  "BNE"                         |
  "BPL"                         |
  "BRK"                         |
  "BVC"                         |
  "BVS"                         |
  "CLC"                         |
  "CLD"                         |
  "CLI"                         |
  "CLV"                         |
  "CMP"                         |
  "CPX"                         |
  "CPY"                         |
  "DEC"                         |
  "DEX"                         |
  "DEY"                         |
  "EOR"                         |
  "INC"                         |
  "INX"                         |
  "INY"                         |
  "JMP"                         |
  "JSR"                         |
  "LDA"                         |
  "LDX"                         |
  "LDY"                         |
  "LSR"                         |
  "NOP"                         |
  "ORA"                         |
  "PHA"                         |
  "PHP"                         |
  "PLA"                         |
  "PLP"                         |
  "ROL"                         |
  "ROR"                         |
  "RTI"                         |
  "RTS"                         |
  "SBC"                         |
  "SEC"                         |
  "SED"                         |
  "SEI"                         |
  "STA"                         |
  "STX"                         |
  "STY"                         |
  "TAX"                         |
  "TAY"                         |
  "TSX"                         |
  "TXA"                         |
  "TXS"                         |
  "TYA"                         { return token(TokenType.KEYWORD); }

  /* string literal */
  \"{StringCharacter}+\"        |

  \'{SingleCharacter}+\         { return token(TokenType.STRING); }


  /* Directives */
  ".assert"                  |
  ".binary"                  |
  ".blk"                     |
  ".blkw"                    |
  ".bsz"                     |
  ".db"                      |
  ".include"                 |
  ".dc"                      |
  ".byte"                    |
  ".org"                     |
  ".word"                    |
  ".string"                  |
  ".zero"                    |
  ".asciiz"                  { return token(TokenType.KEYWORD2); }


  /* labels */
  {Labels}                   { return token(TokenType.TYPE); }


  {Identifier}               { return token(TokenType.IDENTIFIER); }

  {IndexVal}                 |
  {Numbers}                  { return token(TokenType.NUMBER); }

  /* comments */
  {Comment}                   { return token(TokenType.COMMENT); }
  . | {LineTerminator}        { /* skip */ }

}

<<EOF>>                          { return null; }