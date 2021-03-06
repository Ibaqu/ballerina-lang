/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.langserver.completions.util;

import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.TypeDefinitionSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.syntax.tree.ListenerDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModuleVariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.VariableDeclarationNode;
import io.ballerina.projects.Project;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.common.utils.completion.QNameReferenceUtil;
import org.ballerinalang.langserver.commons.BallerinaCompletionContext;
import org.ballerinalang.langserver.commons.DocumentServiceContext;
import org.ballerinalang.langserver.commons.completion.LSCompletionItem;
import org.ballerinalang.langserver.completions.StaticCompletionItem;
import org.ballerinalang.langserver.completions.SymbolCompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Enclose a set of utilities for sorting and ranking of completion items.
 *
 * @since 2.0.0
 */
public class SortingUtil {

    private static final int RANK_UPPER_BOUNDARY = 64;

    private static final int RANK_LOWER_BOUNDARY = 90;

    private static final int RANK_RANGE = 25;

    private static final String BALLERINA_ORG = "ballerina";

    private static final String LANG_LIB_PKG_PREFIX = "lang.";

    private static final String LANG_LIB_LABEL_PREFIX = "ballerina/lang.";

    private static final String BAL_LIB_LABEL_PREFIX = "ballerina/";

    private SortingUtil() {
    }

    /**
     * Check whether the item is an associated module completion item.
     *
     * @param item {@link LSCompletionItem} to evaluate
     * @return {@link Boolean} whether module completion or not
     */
    public static boolean isModuleCompletionItem(LSCompletionItem item) {
        return (item instanceof SymbolCompletionItem
                && ((SymbolCompletionItem) item).getSymbol().orElse(null) instanceof ModuleSymbol)
                || (item instanceof StaticCompletionItem
                && (((StaticCompletionItem) item).kind() == StaticCompletionItem.Kind.MODULE
                || ((StaticCompletionItem) item).kind() == StaticCompletionItem.Kind.LANG_LIB_MODULE));
    }

    /**
     * Check whether the item is an associated type completion item.
     *
     * @param item {@link LSCompletionItem} to evaluate
     * @return {@link Boolean} whether type completion or not
     */
    public static boolean isTypeCompletionItem(LSCompletionItem item) {
        return (item instanceof SymbolCompletionItem
                && ((SymbolCompletionItem) item).getSymbol().orElse(null) instanceof TypeSymbol)
                || (item instanceof StaticCompletionItem
                && ((StaticCompletionItem) item).kind() == StaticCompletionItem.Kind.TYPE);
    }

    /**
     * Get the sort text for a given module completion item.
     *
     * @param context language server completion context
     * @param item    completion item to evaluate
     * @return {@link String} rank assigned to the completion item
     */
    public static String genSortTextForModule(BallerinaCompletionContext context, LSCompletionItem item) {
        /*
        Sorting order is defined as follows,
        (1) Current project's modules
        (2) Imported modules (there is an import statement added)
        (3) Langlib modules
        (4) Standard libraries
        (5) Other modules 
         */
        Optional<Project> currentProject = context.workspace().project(context.filePath());
        String currentOrg = currentProject.get().currentPackage().packageOrg().value();
        String currentPkgName = currentProject.get().currentPackage().packageName().value();
        String label = item.getCompletionItem().getLabel();
        int rank = -1;
        if (item instanceof SymbolCompletionItem && ((SymbolCompletionItem) item).getSymbol().isPresent() &&
                ((SymbolCompletionItem) item).getSymbol().get().kind() == SymbolKind.MODULE) {
            // Already imported module
            ModuleSymbol moduleSymbol = (ModuleSymbol) ((SymbolCompletionItem) item).getSymbol().get();
            String orgName = moduleSymbol.id().orgName();
            String moduleName = moduleSymbol.id().moduleName();

            if (currentOrg.equals(orgName) && moduleName.startsWith(currentPkgName + ".")) {
                // Module in the current project
                rank = 1;
            } else if (BALLERINA_ORG.equals(orgName) && !moduleName.startsWith(LANG_LIB_PKG_PREFIX)) {
                // langLib module
                rank = 2;
            } else if (BALLERINA_ORG.equals(orgName)) {
                // ballerina module
                rank = 3;
            } else {
                // any other imported module
                rank = 4;
            }
        } else if (label.startsWith(LANG_LIB_LABEL_PREFIX)) {
            // Langlib modules
            rank = 5;
        } else if (label.startsWith(BAL_LIB_LABEL_PREFIX)) {
            // Standard libraries modules
            rank = 6;
        }
        rank = rank < 0 ? 7 : rank;

        return genSortText(rank);
    }

    /**
     * Get the sort text for the initializer context items.
     *
     * @param context        language server completion context
     * @param item           Completion Item to evaluate
     * @param assignableType assignable type (derived from the LHS)
     * @return {@link String} generated sort text
     */
    public static String genSortTextForInitContextItem(DocumentServiceContext context, LSCompletionItem item,
                                                       TypeDescKind assignableType) {
        // TODO: Revamp should carry out after fixing the type reference issue in semantic model is fixed 
        /*
        Sorting order is as follows,
        (1) new Keyword and the new(...) snippet
        (2) Variable symbols with the valid assignable type
        (3) Function invocations with the return type mapping the assignable type
        (4) Other Variables
        (5) Other Function Invocations
        (6) Modules
         */
//        String label = item.getCompletionItem().getLabel();
//        if (label.equals("new") || label.startsWith("new(")) {
//            return genSortText(1);
//        }
//        if (item instanceof SymbolCompletionItem) {
//            Symbol symbol = ((SymbolCompletionItem) item).getSymbol();
//            if (symbol instanceof BInvokableSymbol) {
//                BType retType = ((BInvokableSymbol) symbol).retType;
//                if (retType != null && retType.tsymbol == assignableType) {
//                    return genSortText(3);
//                }
//                return genSortText(5);
//            }
//            if (symbol instanceof VariableSymbol) {
//                if (symbol.type.tsymbol == assignableType) {
//                    return genSortText(2);
//                }
//                return genSortText(4);
//            }
//            // TODO: Check whether we come to this point
//            return genSortText(6);
//        }
//        if (isModuleCompletionItem(item)) {
//            return genSortText(7) + genSortTextForModule(context, item);
//        }
//
        return genSortText(8);
    }

    /**
     * Generate the sort text when given the rank.
     * Sort Text is generated by providing an All Caps String which includes only the english alphabetical
     * characters within 65-90 ASCII range.
     *
     * @param rank rank to be assigned. Rank should be a non zero integer
     * @return {@link String} generated sort text
     */
    public static String genSortText(int rank) {
        if (rank < 1) {
            throw new IllegalArgumentException("Rank should be greater than zero");
        }

        int suffixValue = rank % RANK_RANGE;
        String suffix = suffixValue == 0 ? "" : String.valueOf((char) (RANK_UPPER_BOUNDARY + suffixValue));
        String prefix = String.join("", Collections.nCopies((rank - suffixValue) / RANK_RANGE,
                (char) RANK_LOWER_BOUNDARY + ""));

        return prefix + suffix;
    }

    /**
     * Get the assignable type for the node. Assignable type is only addressed for the following at the moment.
     * Assignable type is derived by analyzing the LHS of the particular node and the particular binding pattern
     * 1. Listener Declaration
     * 2. Local variable declaration
     * 3. Module level variable declaration
     *
     * @param context Completion context
     * @param owner   Owner node to extract the assignable type
     * @return {@link Optional} assignable type
     */
    public static Optional<TypeSymbol> getAssignableType(BallerinaCompletionContext context, Node owner) {
        Optional<Node> typeDesc;
        switch (owner.kind()) {
            case LISTENER_DECLARATION:
                typeDesc = Optional.ofNullable(((ListenerDeclarationNode) owner).typeDescriptor().orElse(null));
                break;
            case LOCAL_VAR_DECL:
                typeDesc = Optional.ofNullable(((VariableDeclarationNode) owner).typedBindingPattern()
                        .typeDescriptor());
                break;
            case MODULE_VAR_DECL:
                typeDesc = Optional.ofNullable(((ModuleVariableDeclarationNode) owner).typedBindingPattern()
                        .typeDescriptor());
                break;
            default:
                return Optional.empty();
        }

        if (typeDesc.isEmpty()) {
            return Optional.empty();
        }

        List<Symbol> visibleSymbols = context.visibleSymbols(context.getCursorPosition());
        if (typeDesc.get().kind() == SyntaxKind.QUALIFIED_NAME_REFERENCE) {
            QualifiedNameReferenceNode qNameRef = (QualifiedNameReferenceNode) typeDesc.get();
            String alias = QNameReferenceUtil.getAlias(qNameRef);
            Optional<ModuleSymbol> moduleSymbol = CommonUtil.searchModuleForAlias(context, alias);

            if (moduleSymbol.isEmpty()) {
                return Optional.empty();
            }
            String identifier = qNameRef.identifier().text();
            return CommonUtil.getTypeFromModule(context, alias, identifier);
        }
        if (typeDesc.get().kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            String nameRef = ((SimpleNameReferenceNode) typeDesc.get()).name().text();
            for (Symbol symbol : visibleSymbols) {
                if (symbol.kind() == SymbolKind.TYPE_DEFINITION && symbol.getName().get().equals(nameRef)) {
                    TypeDefinitionSymbol typeDefinitionSymbol = (TypeDefinitionSymbol) symbol;
                    return Optional.of(typeDefinitionSymbol.typeDescriptor());
                }
            }
            return Optional.empty();
        }

        return Optional.empty();
    }

    /**
     * Sets the sorting text to provided completion items using the default sorting.
     *
     * @param context         Completion context
     * @param completionItems Completion items to be set sorting texts
     */
    public static void toDefaultSorting(BallerinaCompletionContext context, List<LSCompletionItem> completionItems) {
        for (LSCompletionItem item : completionItems) {
            int rank = SortingUtil.toRank(item);
            item.getCompletionItem().setSortText(SortingUtil.genSortText(rank));
        }
    }

    /**
     * Calculates the rank of a given completion item with rank offset 0.
     *
     * @param completionItem Completion item
     * @return rank
     * @see #toRank(LSCompletionItem, int)
     */
    public static int toRank(LSCompletionItem completionItem) {
        return toRank(completionItem, 0);
    }

    /**
     * Calculates the rank of a given completion item.
     *
     * @param completionItem Completion item
     * @param rankOffset     Number to offset the rank by
     * @return Rank
     */
    public static int toRank(LSCompletionItem completionItem, int rankOffset) {
        int rank = -1;
        CompletionItemKind completionItemKind = completionItem.getCompletionItem().getKind();
        switch (completionItem.getType()) {
            case SYMBOL:
                if (completionItemKind != null) {
                    switch (completionItemKind) {
                        case Constant:
                            rank = 1;
                            break;
                        case Variable:
                            rank = 2;
                            break;
                        case Function:
                            rank = 3;
                            break;
                        case Method:
                            rank = 4;
                            break;
                        case Constructor:
                            rank = 5;
                            break;
                        case EnumMember:
                            rank = 8;
                            break;
                        case Enum:
                            rank = 9;
                            break;
                        case Class:
                            rank = 10;
                            break;
                        case Interface:
                            rank = 11;
                            break;
                        case Struct:
                            rank = 12;
                            break;
                        case TypeParameter:
                            rank = 13;
                            break;
                        case Module:
                            rank = 14;
                            break;
                    }
                }
                break;
            case SNIPPET:
                if (completionItemKind != null) {
                    switch (completionItemKind) {
                        case TypeParameter:
                            rank = 13;
                            break;
                        case Snippet:
                            rank = 15;
                            break;
                        case Keyword:
                            rank = 16;
                            break;
                    }
                }
                break;
            case OBJECT_FIELD:
                rank = 6;
                break;
            case RECORD_FIELD:
                rank = 7;
                break;
        }

        if (rank == -1) {
            rank = 17;
        } else {
            rank = rankOffset + rank;
        }

        return rank;
    }
}
