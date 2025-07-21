# Resolving Dependency Conflicts via Source Code Modification

This project is part of my Master's thesis focused on resolving dependency conflicts in software projects by analyzing and modifying the source code directly. The goal is to automate the resolution of library version clashes between dependencies by modifying the source code of the broken dependency.

## Approach
1. Detect changes between library versions (for example with japicmp) 
2. Extract relevant changes based on conflict type.
3. Pass relevant changes either to an algorithmic or AI-powered approach to fix the broken dependency.
4. Inject change into broken dependency.
5. Validate that the dependency now works.


## Conflict types
To solve conflicts, one first has to consider how they occur. I have currently identified the following types:

1. Method removed
2. Method visibility changed (for example: from public to private or protected)
3. Method changed from non-static to static
4. Method changed from static to non-static
5. Method parameter(s) added
6. Method parameter(s) removed
7. Method parameter type(s) changed
8. Method annotation changed (for example: deprecated)
9. Method renamed
10. Method invocation with used return value: method changed return type to void
11. Method invocation with used return value: method changed return type (non-void)
12. Method behavior change (for example: throws exception)
13. Static method moved (for example: into a different class)
14. Non-static method moved
15. Method changed to abstract

### Potential conflict resolutions
Based on the conflict types, I can propose potential fixes. I differentiate these automated fixes into three categories: 

1. Deterministically Resolvable: Fixes that are considered trivial and can be applied and verified in an deterministic manner.
2. Heuristically Resolvable: Fixes that rely on assumptions, likely correct but require human validation.
3. Not Resolvable: Fixes that cannot be applied due to missing information or incompatible code.

Furthermore, I add the following prefixes to a given fix based on how confident I am:
- definite
- tentative
- unknown

#### 1. Method removed
Category: tentatively heuristically resolvable

I consider this conflict fixable *most* of the time based on the reasoning that library developers often add alternative methods when removing a method.

Approach: Pass class diff of affected library class where the method got removed to an AI and hope for the best.

#### 2. Method visibility changed
Category: tentatively heuristically resolvable

I consider this conflict fixable *most* of the time based on the reasoning that library developers often add alternative methods when changing the visiblity of methods.

Approach: Pass class diff of affected library class where the method visibility got changed to an AI and hope for the best.

#### 3. Method changed from non-static to static
Category: definitively deterministically resolvable

Minor inconvenience.

Approach: Call the method with the class name instead of the instance.

#### 4. Method changed from static to non-static
Category: tentatively heuristically resolvable

A potential fix would need to instantiate the given class and call the method with the instance. The difficulty of this task depends on the needed parameters and preconditions an instance would need.

Approach: Pass constructors and context snippet of the broken dependency to an AI and hope for the best.

#### 5. Method parameter(s) added
Category: tentatively heuristically resolvable

A potential fix would need to either add default values or dynamically use variables from the context of the dependency. 

Approach: Pass context snippet and method parameter change to an AI and hope for the best.

#### 6. Method parameter(s) removed
Category: definitively deterministically resolvable

Approach: Simply remove the unnecessary variables from the call.

#### 7. Method parameter type(s) changed
Category: tentatively heuristically resolvable

A potential fix would need to either use default values or dynamically use variables from the context of the dependency to replace the parameters of the wrong type. 

Approach: Pass context snippet and method parameter change to an AI and hope for the best.

#### 8. Method annotation changed
Category: tentatively heuristically resolvable

A potential fix would need to identify the severity of the annotation change (some could be harmless) and then act on it (for example by replacing the method call with another if it got deprecated). 

Approach: Pass class diff of affected library class where the method annotation changed to an AI and hope for the best.

#### 9. Method renamed
Category: unknown heuristically resolvable

The difficulty of this conflict is identifying which method is the newly renamed one (for example by parameter matching). Even identifying that the method got renamed would be difficult with just a class diff alone. Maybe version patch notes could help here?

Approach: Pass class diff of affected library class where the method got renamed along with version changes (for example from Git) of the library to an AI and hope for the best.

#### 10. Method invocation with used return value: method changed return type to void
Category: tentatively not resolvable

The difficulty of this conflict are the side effects of the missing return value. Fixing this would mean getting the value from somewhere else (maybe by calling a different method).

Approach: Currently considered unfeasible by me.

#### 11. Method invocation with used return value: method changed return type (non-void)
Category: tentatively not resolvable

The difficulty of this conflict are the side effects of the missing return value. Fixing this would mean getting the value from somewhere else (maybe by calling a different method).

Approach: Currently considered unfeasible by me.

#### 12. Method behavior change (for example: throws exception)
Category: tentatively deterministically resolvable

Approach: To resolve behavior changes, I would wrap the call in a try catch block and hope for the best.

#### 13. Static method moved
Category: definitively deterministically resolvable

Approach: Replace the old calling class of the invocation with the new class the method is now located in.

#### 14. Non-static method moved
Category:  tentatively heuristically resolvable

This would need an instance of the new class where the method now resides. I consider this not feasible based on conflict type 4.

Approach: Same as type 4.

#### 15. Method changed to abstract
Category:  tentatively not resolvable

This would need an instance of a child of the class that implements the method which I consider too much guesswork.
