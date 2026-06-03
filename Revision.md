# Objetivos del análisis léxico y tabla de símbolos


## Objetivo general


Realizar el análisis léxico del código fuente para identificar todos los elementos que lo conforman, clasificarlos correctamente como tokens y registrar su información principal, considerando también el uso de una tabla de símbolos con atributos para apoyar las siguientes fases del compilador.


## Objetivos específicos


### 1. Realizar el barrido léxico del código


Recorrer el código fuente para encontrar cada uno de los elementos que aparecen dentro del programa.


El analizador léxico debe identificar elementos como:


* Identificadores
* Operadores
* Palabras reservadas
* Números
* Estados
* Símbolos válidos del lenguaje


### 2. Identificar los tokens encontrados


Reconocer cada token encontrado durante el barrido léxico y determinar qué tipo de elemento representa.


Por ejemplo:


* `voltaje` puede identificarse como un identificador.
* `>=` debe identificarse como un operador.
* `estado` puede analizarse para determinar si es palabra reservada o identificador.
* `pico` puede analizarse según las palabras reservadas definidas en el lenguaje.


### 3. Registrar línea y columna de cada token


Guardar la ubicación de cada token encontrado dentro del código fuente.


Para cada elemento identificado se debe registrar:


* Token encontrado
* Tipo de token
* Línea donde aparece
* Columna donde aparece


### 4. Clasificar correctamente los elementos del lenguaje


Diferenciar si un elemento corresponde a un identificador, operador o palabra reservada.


El analizador no debe tratar todos los elementos como identificadores, ya que algunos símbolos o palabras tienen una función específica dentro del lenguaje.


Por ejemplo:


* Un identificador representa un nombre definido dentro del programa.
* Un operador representa una operación o comparación.
* Una palabra reservada representa una instrucción propia del lenguaje.


### 5. Considerar la tabla de símbolos


Utilizar una tabla de símbolos para almacenar información de los identificadores encontrados en el programa.


La tabla de símbolos debe servir como apoyo para guardar datos importantes de cada identificador y proporcionar información adicional a las siguientes fases del compilador.


### 6. Agregar atributos a la tabla de símbolos


No guardar únicamente el nombre del identificador, sino también sus atributos.


Los atributos pueden incluir información como:


* Tipo de dato
* Si contiene un número
* Si corresponde a una variable
* Información definida durante el análisis semántico


### 7. Definir los tipos de datos


Considerar los tipos de datos que manejará el lenguaje.


Si el lenguaje utiliza números, se deben contemplar sus tipos correspondientes.


En caso de manejar números con punto decimal, también deben considerarse dentro de los tipos de datos definidos.


### 8. Asignar tipo de dato a los identificadores


Registrar el tipo de dato como atributo dentro de la tabla de símbolos.


Por ejemplo, si existe el identificador:


```txt
voltaje
```


uno de sus atributos puede ser:


```txt
tipo entero
```


o el tipo que corresponda según la definición del lenguaje.


### 9. Usar la tabla de símbolos como apoyo para otras fases


Permitir que la tabla de símbolos proporcione información al análisis sintáctico y semántico.


Cuando se encuentre un identificador, se puede consultar la tabla de símbolos para saber si tiene atributos asignados, como su tipo de dato.


### 10. Diferenciar errores léxicos y semánticos


No considerar automáticamente como error léxico un identificador que no fue declarado.


Si una palabra cumple con la estructura de identificador, el analizador léxico puede reconocerla como identificador.


Si el identificador no fue declarado previamente, el problema corresponde al análisis semántico.


### 11. Detectar posibles errores de escritura


Cuando un identificador no tenga atributos o no corresponda correctamente con lo esperado, se puede revisar si el usuario escribió mal una palabra reservada.


El sistema puede comparar el texto escrito con la lista de palabras reservadas para encontrar cuál se parece más.


Esto puede ayudar a detectar casos donde el usuario escribió una palabra incompleta, invertida o con letras cambiadas.


### 12. Comparar con palabras reservadas


Implementar una comparación entre el texto ingresado y las palabras reservadas del lenguaje.


Si el texto se parece a una palabra reservada, se puede generar una sugerencia indicando lo que probablemente se quiso escribir.


### 13. Utilizar autómatas o listas de palabras reservadas


Aplicar autómatas o listas de palabras reservadas para reconocer los elementos del lenguaje.


Si se usan autómatas, el sistema debe revisar si existe un recorrido válido para el token encontrado.


Si se usan listas, el sistema puede comparar el token con las palabras reservadas definidas.


### 14. Generar información útil para el compilador


Asegurar que el análisis léxico y la tabla de símbolos proporcionen información necesaria para las fases posteriores.


La información generada debe ayudar a validar estructuras gramaticales, revisar identificadores y detectar errores de forma más clara.
