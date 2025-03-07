/**
 * Copyright 2018-2021 LinkedIn Corporation. All rights reserved.
 * Licensed under the BSD-2 Clause license.
 * See LICENSE in the project root for license information.
 */
package com.linkedin.coral.hive.hive2rel.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.sql.type.SqlReturnTypeInference;


/**
 * A utility class to coalesce the {@link RelDataType} of struct between Trino's representation and
 * hive's extract_union UDF's representation on exploded union.
 *
 */
public class CoalesceStructUtility {

  /**
   * The semantics for the extract_union is now pass-through: Assuming the engine's reader could deal with
   * union type and explode it into a struct, this extract_union UDF's return type will simply follow exploded struct's
   * schema based on how many arguments passed by users.
   */
  public static final SqlReturnTypeInference EXTRACT_UNION_FUNCTION_RETURN_STRATEGY = opBinding -> {
    int numArgs = opBinding.getOperandCount();
    Preconditions.checkState(numArgs == 1 || numArgs == 2);
    // 1-arg case
    if (numArgs == 1) {
      return opBinding.getOperandType(0);
    }
    // 2-arg case
    else {
      int ordinal = opBinding.getOperandLiteralValue(1, Integer.class);
      return opBinding.getOperandType(0).getFieldList().get(ordinal).getType();
    }
  };

  /**
   * Represents the return type for the coalesce_struct UDF that is built for bridging the schema difference
   * between extract_union UDF's processed schema of union field in Coral IR (let's call it struct_ex) and
   * Trino's schema when deserializing union field from its reader.
   * (Let's call it struct_tr, See https://github.com/trinodb/trino/pull/3483 for details).
   *
   * The main reason we need this briding capability is that we have existing users relying on the
   * schema of struct_ex. While the underlying reader(e.g. the trino one referenced above) starts to interpret the union
   * in its own format, Coral tries to maintain backward compatibility on top of that. Notably we also have
   * Iceberg reader does the same, see Linkedin's (temporary) fork on Iceberg:
   * https://github.com/linkedin/iceberg/pull/84 (Avro)
   * https://github.com/linkedin/iceberg/pull/85 (ORC)
   *
   *
   * Further details:
   * struct_tr looks like:
   * struct&lt;tag:int, field0:type0, field1:type1, ... fieldN:typeN&gt;
   *
   * struct_ex looks like:
   * struct&lt;tag_0:type0, tag_1:type1, ... tag_N:typeN&gt;
   *
   * This new UDF could be stated as the following signatures:
   * def coalesce_struct(struct:struct_tr) : struct_ex = {...}
   * def coalesce_struct(struct:struct_tr, ordinal: int): field_at_ordinal = {...}
   *
   */
  public static final SqlReturnTypeInference COALESCE_STRUCT_FUNCTION_RETURN_STRATEGY = opBinding -> {
    int numArgs = opBinding.getOperandCount();
    RelDataTypeFactory typeFactory = opBinding.getTypeFactory();
    Preconditions.checkState(numArgs == 1 || numArgs == 2);
    RelDataType coalescedDataType = coalesce(opBinding.getOperandType(0), typeFactory);
    // 1-arg case
    if (numArgs == 1) {
      return coalescedDataType;
    }
    // 2-arg case
    else {
      int ordinal = opBinding.getOperandLiteralValue(1, Integer.class);
      return coalescedDataType.getFieldList().get(ordinal).getType();
    }
  };
  private static final String TRINO_PREFIX = "field";
  private static final String HIVE_EXTRACT_UNION_PREFIX = "tag_";

  private CoalesceStructUtility() {
    // Utility class, does nothing in constructor
  }

  /**
   * Converting a {@link RelDataType} that could potentially contain a Trino-format exploded-union(i.e. a struct
   * in a format of {tag, field0, field1, ..., fieldN} to represent a union after being deserialized)
   * into a exploded-union that complies with Hive's extract_union UDF format
   * (i.e. a struct as {tag_0, tag_1, ..., tag_{N}} to represent a union after being deserialized)
   *
   * For more information, check: https://github.com/trinodb/trino/pull/3483
   */
  @VisibleForTesting
  static RelDataType coalesce(RelDataType inputNode, RelDataTypeFactory typeFactory) {
    // Using type information implicitly carried in the object of RelDateType
    // instead of getting down to SqlTypeName since the former contains enough categorization
    // of types to achieve the purpose for this method.

    if (inputNode.isStruct()) {
      List<String> fieldNames = inputNode.getFieldNames();
      return coalesceStruct(inputNode, isTrinoStructPattern(fieldNames), typeFactory);
    } else if (inputNode.getKeyType() != null) {
      return coalesceMap(inputNode, typeFactory);
    } else if (inputNode.getComponentType() != null) {
      return coalesceCollection(inputNode, typeFactory);
    } else {
      return inputNode;
    }
  }

  private static RelDataType coalesceStruct(RelDataType inputNode, boolean coalesceRequired,
      RelDataTypeFactory typeFactory) {
    List<String> originalNames = inputNode.getFieldNames();
    List<String> convertedNames =
        coalesceRequired ? originalNames.stream().map(x -> x.replaceFirst(TRINO_PREFIX, HIVE_EXTRACT_UNION_PREFIX))
            .collect(Collectors.toList()).subList(1, originalNames.size()) : originalNames;
    List<RelDataType> originalTypes =
        inputNode.getFieldList().stream().map(RelDataTypeField::getType).collect(Collectors.toList());
    List<RelDataType> convertedTypes =
        new ArrayList<>(coalesceRequired ? originalTypes.size() - 1 : originalTypes.size());
    for (int i = coalesceRequired ? 1 : 0; i < originalTypes.size(); i++) {
      convertedTypes.add(coalesce(originalTypes.get(i), typeFactory));
    }

    return typeFactory.createStructType(convertedTypes, convertedNames);
  }

  private static RelDataType coalesceMap(RelDataType inputNode, RelDataTypeFactory typeFactory) {
    RelDataType coalescedKeyType = coalesce(inputNode.getKeyType(), typeFactory);
    RelDataType coalescedValueType = coalesce(inputNode.getValueType(), typeFactory);
    return typeFactory.createMapType(coalescedKeyType, coalescedValueType);
  }

  private static RelDataType coalesceCollection(RelDataType inputNode, RelDataTypeFactory typeFactory) {
    RelDataType coalescedComponentType = coalesce(inputNode.getComponentType(), typeFactory);
    return typeFactory.createArrayType(coalescedComponentType, -1);
  }

  /**
   * Trino's pattern has two elements:
   * - The first element has to be "tag".
   * - The following elements have to follow the naming pattern as "field{N}" where N
   * represents the position of this element in the struct, starting from 0.
   */
  @VisibleForTesting
  static boolean isTrinoStructPattern(List<String> fieldNames) {
    if (fieldNames.isEmpty() || !fieldNames.get(0).equals("tag")) {
      return false;
    } else {
      boolean flag = true;
      StringBuilder fieldNameRef = new StringBuilder("field");
      for (int i = 1; i < fieldNames.size(); i++) {
        int index = i - 1;
        fieldNameRef.append(index);
        if (!fieldNameRef.toString().equals(fieldNames.get(i))) {
          flag = false;
          break;
        }
        fieldNameRef.delete(5, fieldNameRef.length());
      }
      return flag;
    }
  }
}
