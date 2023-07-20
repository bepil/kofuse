# kofuse

![Build](https://github.com/bepil/kofuse/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/22222-kofuse.svg)](https://plugins.jetbrains.com/plugin/22222-kofuse)

<!-- Plugin description -->
Kofuse allows you to find Kotlin functions and methods by their type signature. To search using Kofuse, open the 
*Navigate* menu and press *Kofuse*. Types need to be entered with their fully qualified name.


![](https://raw.githubusercontent.com/bepil/kofuse/d50b210963af06a9651516573beee05aac9a97b9/doc/assets/search.png)
<!-- Plugin description end -->


## Installation
To install Kofuse into Intellij IDEA, follow the steps below:
1. Go into the *Preferences* of IntelliJ IDEA;
2. On the left, click on the *Plugins* section;
3. Click on the *Marketplace* tab;
4. Search for *Kofuse*;
5. Click *Install*.

### Run Kofuse from sources
To run Kofuse from sources:
1. Clone or download this repository;
2. Open the project in IntelliJ IDEA;
3. Run the *Run Plugin* configuration from IntelliJ IDEA.

## Known limitations

There are several limitations in Kofuse:
- Kofuse only finds exact textual matches. Types need to be entered with the full fully qualified name. 
A partial match or a text with typos will not give results;
- Kofuse does not take sub and super types into account. 
For example, searching for a `List` will not find an `ArrayList`;
- Kofuse does not understand generics. For example, it is possible to search for `List`, but not for `List<String>`;
- It is not possible to search for function lambda types, such as `(String) -> Int`;
- Kofuse indexes into memory. A restart of the IDE results in a reindex;
- If the return type of a function changes without the file containing the function being changed, the Kofuse index will
be out of sync. In the following example, If `fun b` is changed to return, for example, an `Int`, then `a`'s return type 
isn't updated in the index:
```
fun a() = b()

// Some other file:
fun b = ""
```

## License, warranty and liability
See [the license](LICENSE).