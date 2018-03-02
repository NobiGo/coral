 package com.linkedin.coral.functions;

 import com.google.common.collect.HashMultimap;
 import com.google.common.collect.ImmutableList;
 import com.google.common.collect.Multimap;
 import java.util.Collection;
 import java.util.Collections;
 import java.util.List;
 import org.apache.calcite.rel.type.RelDataType;
 import org.apache.calcite.rel.type.RelDataTypeFactory;
 import org.apache.calcite.runtime.PredicateImpl;
 import org.apache.calcite.sql.SqlIdentifier;
 import org.apache.calcite.sql.SqlOperator;
 import org.apache.calcite.sql.SqlOperatorBinding;
 import org.apache.calcite.sql.parser.SqlParserPos;
 import org.apache.calcite.sql.type.ReturnTypes;
 import org.apache.calcite.sql.type.SqlOperandTypeChecker;
 import org.apache.calcite.sql.type.SqlOperandTypeInference;
 import org.apache.calcite.sql.type.SqlReturnTypeInference;
 import org.apache.calcite.sql.type.SqlTypeFamily;
 import org.apache.calcite.sql.type.SqlTypeName;
 import org.apache.calcite.sql.validate.SqlUserDefinedFunction;

 import static org.apache.calcite.sql.fun.SqlStdOperatorTable.*;
 import static org.apache.calcite.sql.type.OperandTypes.*;
 import static org.apache.calcite.sql.type.ReturnTypes.*;


/**
 * Static implementation of HiveFunctionRegistry that has hard-coded list of all
 * function names. This has a major disadvantage that the user defined functions are
 * not available to the registry without manually adding the entry here and uploading
 * a new version of library.
 *
 * TODO: Provide function registry catalog
 */
public class StaticHiveFunctionRegistry implements HiveFunctionRegistry {

  static final Multimap<String, HiveFunction> FUNCTION_MAP = HashMultimap.create();
  // NOTE: all function names should be lowercase for case-insensitive comparison
  static {
    // FIXME: This mapping is currently incomplete
    // aggregation functions
    addFunctionEntry("sum", SUM);
    addFunctionEntry("count", COUNT);
    addFunctionEntry("avg", AVG);
    addFunctionEntry("min", MIN);
    addFunctionEntry("max", MAX);

    //addFunctionEntry("in", HiveInOperator.IN);
    FUNCTION_MAP.put("in", HiveFunction.IN);

    //addFunctionEntry("in", SqlStdOperatorTable.IN);

    // operators
    addFunctionEntry("RLIKE", HiveRLikeOperator.RLIKE);
    addFunctionEntry("REGEXP", HiveRLikeOperator.REGEXP);
    addFunctionEntry("!=", NOT_EQUALS);
    addFunctionEntry("==", EQUALS);

    // conditional function
    addFunctionEntry("tok_isnull", IS_NULL);
    addFunctionEntry("tok_isnotnull", IS_NOT_NULL);
    FUNCTION_MAP.put("when", HiveFunction.WHEN);
    FUNCTION_MAP.put("case", HiveFunction.CASE);
    FUNCTION_MAP.put("between", HiveFunction.BETWEEN);
    addFunctionEntry("nullif", NULLIF);
    addFunctionEntry("isnull", IS_NULL);
    addFunctionEntry("isnotnull", IS_NOT_NULL);

    // TODO: this should be arg1 or arg2 nullable
    createAddUserDefinedFunction("nvl", ARG0_NULLABLE,
        and(family(SqlTypeFamily.ANY, SqlTypeFamily.ANY), SAME_SAME));

    // calcite models 'if' function as CASE operator. We can use CASE but that will cause translation
    // to SQL to be odd although correct. So, we add 'if' as UDF
    // TODO: add check to verify 2nd and 3rd operands are same
    addFunctionEntry("if",
        createCalciteUDF("if", HiveReturnTypes.ARG1_OR_ARG2, OperandTypeInference.BOOLEAN_ANY_SAME,
            new SameOperandTypeExceptFirstOperandChecker(3, SqlTypeName.BOOLEAN),
            null));

    addFunctionEntry("coalesce", COALESCE);
    // cast operator
    addCastOperatorEntries();

    // Complex type constructors
    addFunctionEntry("array", ARRAY_VALUE_CONSTRUCTOR);
    addFunctionEntry("struct", ROW);
    addFunctionEntry("map", MAP_VALUE_CONSTRUCTOR);
    addFunctionEntry("named_struct", HiveNamedStructFunction.NAMED_STRUCT);

    // conversion functions
    createAddUserDefinedFunction("binary", HiveReturnTypes.BINARY,
        or(family(SqlTypeFamily.STRING), family(SqlTypeFamily.BINARY)));

    // mathematical functions
    createAddUserDefinedFunction("hex", HiveReturnTypes.STRING,
        or(family(SqlTypeFamily.STRING), family(SqlTypeFamily.NUMERIC), family(SqlTypeFamily.BINARY)));

    // string functions
    // TODO: operand types are not strictly true since these functions can take null literal
    // and most of these entries don't allow null literals. This will work for most common usages
    // but it's easy to write HiveQL to make these fail
    createAddUserDefinedFunction("ascii", INTEGER, STRING);
    createAddUserDefinedFunction("base64", HiveReturnTypes.STRING, BINARY);
    createAddUserDefinedFunction("character_length", INTEGER, STRING);
    createAddUserDefinedFunction("chr", HiveReturnTypes.STRING, NUMERIC);
    createAddUserDefinedFunction("concat", HiveReturnTypes.STRING, SAME_VARIADIC);
    createAddUserDefinedFunction("concat_ws", HiveReturnTypes.STRING,
        family(SqlTypeFamily.STRING, SqlTypeFamily.ARRAY));
    createAddUserDefinedFunction("concat_ws", HiveReturnTypes.STRING, SAME_VARIADIC);
    createAddUserDefinedFunction("context_ngrams", LEAST_RESTRICTIVE,
        family(SqlTypeFamily.ARRAY, SqlTypeFamily.ARRAY, SqlTypeFamily.INTEGER, SqlTypeFamily.INTEGER));
    createAddUserDefinedFunction("decode", HiveReturnTypes.STRING, family(SqlTypeFamily.BINARY, SqlTypeFamily.STRING));
    createAddUserDefinedFunction("elt", HiveReturnTypes.STRING, VARIADIC);
    createAddUserDefinedFunction("encode", HiveReturnTypes.BINARY, STRING_STRING);
    createAddUserDefinedFunction("field", INTEGER, VARIADIC);
    createAddUserDefinedFunction("find_in_set", INTEGER, STRING_STRING);
    createAddUserDefinedFunction("format_number", HiveReturnTypes.STRING, NUMERIC_INTEGER);
    createAddUserDefinedFunction("get_json_object", HiveReturnTypes.STRING, STRING_STRING);
    createAddUserDefinedFunction("in_file", ReturnTypes.BOOLEAN, STRING_STRING);
    createAddUserDefinedFunction("initcap", HiveReturnTypes.STRING, STRING);
    createAddUserDefinedFunction("instr", INTEGER, STRING_STRING);
    createAddUserDefinedFunction("length", INTEGER_NULLABLE, STRING);
    createAddUserDefinedFunction("levenshtein", INTEGER, STRING_STRING);
    createAddUserDefinedFunction("locate", HiveReturnTypes.STRING,
        family(ImmutableList.of(SqlTypeFamily.STRING, SqlTypeFamily.STRING, SqlTypeFamily.INTEGER), optionalOrd(2)));
    addFunctionEntry("lower", LOWER);
    addFunctionEntry("lcase", LOWER);
    createAddUserDefinedFunction("lpad", HiveReturnTypes.STRING,
        family(SqlTypeFamily.STRING, SqlTypeFamily.INTEGER, SqlTypeFamily.STRING));
    createAddUserDefinedFunction("ltrim", HiveReturnTypes.STRING, STRING);
    createAddUserDefinedFunction("ngrams", LEAST_RESTRICTIVE,
        family(SqlTypeFamily.ARRAY, SqlTypeFamily.INTEGER, SqlTypeFamily.INTEGER, SqlTypeFamily.INTEGER));
    createAddUserDefinedFunction("octet_length", INTEGER, STRING);
    createAddUserDefinedFunction("parse_url", HiveReturnTypes.STRING,
        family(Collections.nCopies(3, SqlTypeFamily.STRING), optionalOrd(2)));
    createAddUserDefinedFunction("printf", HiveReturnTypes.STRING, VARIADIC);
    createAddUserDefinedFunction("regexp_extract", ARG0, STRING_STRING_INTEGER);
    createAddUserDefinedFunction("regexp_replace", HiveReturnTypes.STRING, STRING_STRING_STRING);
    createAddUserDefinedFunction("repeat", HiveReturnTypes.STRING, family(SqlTypeFamily.STRING, SqlTypeFamily.INTEGER));
    addFunctionEntry("replace", REPLACE);
    createAddUserDefinedFunction("reverse", ARG0, or(STRING, NULLABLE_LITERAL));
    createAddUserDefinedFunction("rpad", HiveReturnTypes.STRING,
        family(SqlTypeFamily.STRING, SqlTypeFamily.INTEGER, SqlTypeFamily.STRING));
    createAddUserDefinedFunction("rtrim", HiveReturnTypes.STRING, STRING);
    createAddUserDefinedFunction("sentences", LEAST_RESTRICTIVE, STRING_STRING_STRING);
    createAddUserDefinedFunction("soundex", HiveReturnTypes.STRING, STRING);
    createAddUserDefinedFunction("space", HiveReturnTypes.STRING, NUMERIC);
    createAddUserDefinedFunction("split", HiveReturnTypes.arrayOfType(SqlTypeName.VARCHAR), STRING_STRING);
    createAddUserDefinedFunction("str_to_map", HiveReturnTypes.mapOfType(SqlTypeName.VARCHAR, SqlTypeName.VARCHAR),
        family(Collections.nCopies(3, SqlTypeFamily.STRING), optionalOrd(ImmutableList.of(1, 2))));
    addFunctionEntry("substr", SUBSTRING);
    addFunctionEntry("substring", SUBSTRING);
    createAddUserDefinedFunction("substring_index", HiveReturnTypes.STRING, STRING_STRING_INTEGER);
    createAddUserDefinedFunction("translate", HiveReturnTypes.STRING, STRING_STRING_STRING);
    createAddUserDefinedFunction("trim", HiveReturnTypes.STRING, STRING);
    createAddUserDefinedFunction("unbase64", explicit(SqlTypeName.VARBINARY), or(STRING, NULLABLE_LITERAL));
    addFunctionEntry("upper", UPPER);
    addFunctionEntry("ucase", UPPER);

    // Date Functions
    createAddUserDefinedFunction("from_unixtime", HiveReturnTypes.STRING,
        family(ImmutableList.of(SqlTypeFamily.NUMERIC, SqlTypeFamily.STRING), optionalOrd(1)));
    createAddUserDefinedFunction("unix_timestamp", BIGINT,
        family(ImmutableList.of(SqlTypeFamily.STRING, SqlTypeFamily.STRING), optionalOrd(ImmutableList.of(0, 1))));
    createAddUserDefinedFunction("to_date", HiveReturnTypes.STRING,
        or(STRING, DATETIME));
    createAddUserDefinedFunction("year", INTEGER, STRING);
    createAddUserDefinedFunction("quarter", INTEGER, STRING);
    createAddUserDefinedFunction("month", INTEGER, STRING);
    createAddUserDefinedFunction("day", INTEGER, STRING);
    createAddUserDefinedFunction("dayofmonth", INTEGER, STRING);
    createAddUserDefinedFunction("hour", INTEGER, or(STRING, DATETIME));
    createAddUserDefinedFunction("minute", INTEGER, STRING);
    createAddUserDefinedFunction("second", INTEGER, STRING);
    createAddUserDefinedFunction("weekofyear", INTEGER, STRING);
    //TODO: add extract UDF
    createAddUserDefinedFunction("datediff", INTEGER, STRING_STRING);
    createAddUserDefinedFunction("date_add", HiveReturnTypes.STRING,
        or(family(SqlTypeFamily.DATE, SqlTypeFamily.INTEGER), family(SqlTypeFamily.TIMESTAMP, SqlTypeFamily.INTEGER),
            family(SqlTypeFamily.STRING, SqlTypeFamily.INTEGER)));

    createAddUserDefinedFunction("date_sub", HiveReturnTypes.STRING,
        or(family(SqlTypeFamily.DATE, SqlTypeFamily.INTEGER), family(SqlTypeFamily.TIMESTAMP, SqlTypeFamily.INTEGER),
            family(SqlTypeFamily.STRING, SqlTypeFamily.INTEGER)));
    createAddUserDefinedFunction("from_utc_timestamp", explicit(SqlTypeName.TIMESTAMP),
        family(SqlTypeFamily.ANY, SqlTypeFamily.STRING));
    addFunctionEntry("current_date", CURRENT_DATE);
    addFunctionEntry("current_timestamp", CURRENT_TIMESTAMP);
    createAddUserDefinedFunction("add_months", HiveReturnTypes.STRING,
        family(SqlTypeFamily.STRING, SqlTypeFamily.INTEGER));
    createAddUserDefinedFunction("last_day", HiveReturnTypes.STRING, STRING);
    createAddUserDefinedFunction("next_day", HiveReturnTypes.STRING, STRING_STRING);
    createAddUserDefinedFunction("trunc", HiveReturnTypes.STRING, STRING_STRING);
    createAddUserDefinedFunction("months_between", DOUBLE,
        family(SqlTypeFamily.DATE, SqlTypeFamily.DATE));
    createAddUserDefinedFunction("date_format", HiveReturnTypes.STRING,
        or(family(SqlTypeFamily.DATE, SqlTypeFamily.INTEGER), family(SqlTypeFamily.TIMESTAMP, SqlTypeFamily.INTEGER),
            family(SqlTypeFamily.STRING, SqlTypeFamily.INTEGER)));

    // Collection functions
    addFunctionEntry("size", CARDINALITY);
    createAddUserDefinedFunction("array_contains", ReturnTypes.BOOLEAN,
        family(SqlTypeFamily.ARRAY, SqlTypeFamily.ANY));
    createAddUserDefinedFunction("map_keys", new SqlReturnTypeInference() {
      @Override
      public RelDataType inferReturnType(SqlOperatorBinding opBinding) {
        RelDataType operandType = opBinding.getOperandType(0);
        RelDataTypeFactory typeFactory = opBinding.getTypeFactory();
        return typeFactory.createArrayType(operandType.getKeyType(), -1);
      }
    }, family(SqlTypeFamily.MAP));

    createAddUserDefinedFunction("map_values", new SqlReturnTypeInference() {
      @Override
      public RelDataType inferReturnType(SqlOperatorBinding opBinding) {
        RelDataType operandType = opBinding.getOperandType(0);
        RelDataTypeFactory typeFactory = opBinding.getTypeFactory();
        return typeFactory.createArrayType(operandType.getValueType(), -1);
      }
    }, family(SqlTypeFamily.MAP));

    createAddUserDefinedFunction("array_contains", ReturnTypes.BOOLEAN, family(SqlTypeFamily.ARRAY, SqlTypeFamily.ANY));
    createAddUserDefinedFunction("sort_array", ARG0, ARRAY);

    // LinkedIn UDFs: Dali stores mapping from UDF name to the implementing Java class as table properties
    // in the HCatalog. So, an UDF implementation may be referred by different names by different views.
    // We register these UDFs by the implementing class name to create a single entry for each UDF.
    createAddUserDefinedFunction("com.linkedin.dali.udf.istestmemberid.hive.IsTestMemberId", ReturnTypes.BOOLEAN,
        family(SqlTypeFamily.NUMERIC, SqlTypeFamily.CHARACTER));
    createAddUserDefinedFunction("com.linkedin.dali.udf.urnextractor.hive.UrnExtractor",
        HiveReturnTypes.ARRAY_OF_STR_STR_MAP,
        or(STRING, ARRAY));
    createAddUserDefinedFunction("com.linkedin.udf.hdfs.GetDatasetNameFromPathUDF", HiveReturnTypes.STRING, STRING);
    createAddUserDefinedFunction("com.linkedin.dali.udf.isguestmemberid.hive.IsGuestMemberId", ReturnTypes.BOOLEAN,
        NUMERIC);
    createAddUserDefinedFunction("com.linkedin.dali.udf.watbotcrawlerlookup.hive.WATBotCrawlerLookup",
        HiveReturnTypes.rowOf(ImmutableList.of("iscrawler", "crawlerid"), ImmutableList.of(SqlTypeName.BOOLEAN, SqlTypeName.VARCHAR)),
        family(ImmutableList.of(SqlTypeFamily.STRING, SqlTypeFamily.STRING, SqlTypeFamily.STRING, SqlTypeFamily.STRING),
            optionalOrd(ImmutableList.of(2, 3))));

    createAddUserDefinedFunction("com.linkedin.dali.udf.userinterfacelookup.hive.UserInterfaceLookup",
        HiveReturnTypes.STRING, or(family(Collections.nCopies(8, SqlTypeFamily.STRING)),
            family(SqlTypeFamily.STRING, SqlTypeFamily.STRING, SqlTypeFamily.STRING, SqlTypeFamily.STRING, SqlTypeFamily.NUMERIC,
                SqlTypeFamily.STRING, SqlTypeFamily.STRING, SqlTypeFamily.STRING)));
    createAddUserDefinedFunction("com.linkedin.dali.udf.portallookup.hive.PortalLookup",
        HiveReturnTypes.STRING, STRING_STRING_STRING);
    createAddUserDefinedFunction("com.linkedin.dali.udf.useragentparser.hive.UserAgentParser",
        HiveReturnTypes.STRING, STRING_STRING);
    createAddUserDefinedFunction("com.linkedin.dali.udf.maplookup.hive.MapLookup",
        HiveReturnTypes.STRING, family(SqlTypeFamily.MAP, SqlTypeFamily.STRING, SqlTypeFamily.STRING));
    createAddUserDefinedFunction("com.linkedin.dali.udf.monarch.UrnGenerator", HiveReturnTypes.STRING, VARIADIC);
    createAddUserDefinedFunction("com.linkedin.dali.udf.genericlookup.hive.GenericLookup",
        HiveReturnTypes.STRING, ANY);
    createAddUserDefinedFunction("com.linkedin.tscp.reporting.dali.udfs.UrnToID",
        HiveReturnTypes.STRING, STRING);

    createAddUserDefinedFunction("com.linkedin.dali.udf.date.hive.DateFormatToEpoch", BIGINT_NULLABLE,
        STRING_STRING_STRING);
    createAddUserDefinedFunction("com.linkedin.dali.udf.date.hive.EpochToEpochMilliseconds", BIGINT_NULLABLE, NUMERIC);
    createAddUserDefinedFunction("com.linkedin.dali.udf.date.hive.EpochToDateFormat",
        HiveReturnTypes.STRING, family(SqlTypeFamily.NUMERIC, SqlTypeFamily.STRING, SqlTypeFamily.STRING));
    createAddUserDefinedFunction("com.linkedin.dali.udf.sanitize.hive.Sanitize", HiveReturnTypes.STRING, STRING);

    // UDTFs
    addFunctionEntry("explode", HiveExplodeOperator.EXPLODE);
    // FOR UNIT TESTING
    createAddUserDefinedFunction("com.linkedin.coral.hive.hive2rel.CoralTestUDF", ReturnTypes.BOOLEAN,
        family(SqlTypeFamily.INTEGER));
  }

  /**
   * Returns a list of functions matching given name. This returns empty list if the
   * function name is not found
   * @param functionName function name to match
   * @return list of matching HiveFunctions or empty collection.
   */
  @Override
  public Collection<HiveFunction> lookup(String functionName, boolean isCaseSensitive) {
    String name = isCaseSensitive ? functionName : functionName.toLowerCase();
    return FUNCTION_MAP.get(name);
  }

  private static void addFunctionEntry(String functionName, SqlOperator operator) {
    FUNCTION_MAP.put(functionName, new HiveFunction(functionName, operator));
  }

  private static void createAddUserDefinedFunction(String functionName, SqlReturnTypeInference returnTypeInference,
      SqlOperandTypeChecker operandTypeChecker) {
    addFunctionEntry(functionName, createCalciteUDF(functionName, returnTypeInference, operandTypeChecker));
  }

  private static void createAddUserDefinedFunction(String functionName, SqlReturnTypeInference returnTypeInference) {
    addFunctionEntry(functionName, createCalciteUDF(functionName, returnTypeInference));
  }

  private static SqlOperator createCalciteUDF(String functionName, SqlReturnTypeInference returnTypeInference,
      SqlOperandTypeInference operandTypeInference, SqlOperandTypeChecker operandTypeChecker,
      List<RelDataType> paramTypes) {
    return new SqlUserDefinedFunction(new SqlIdentifier(functionName, SqlParserPos.ZERO), returnTypeInference,
        operandTypeInference, operandTypeChecker, paramTypes, null);
  }

  private static SqlOperator createCalciteUDF(String functionName, SqlReturnTypeInference returnTypeInference,
      SqlOperandTypeChecker operandTypeChecker) {
    return new SqlUserDefinedFunction(new SqlIdentifier(functionName, SqlParserPos.ZERO), returnTypeInference, null,
        operandTypeChecker, null, null);
  }

  private static SqlOperator createCalciteUDF(String functionName, SqlReturnTypeInference returnTypeInference) {
    return createCalciteUDF(functionName, returnTypeInference, null);
  }

  private static void addCastOperatorEntries() {
    String[] castFunctions =
        { "tok_boolean", "tok_int", "tok_string", "tok_double", "tok_float", "tok_bigint",
            "tok_tinyint", "tok_smallint", "tok_char", "tok_decimal", "tok_varchar", "tok_binary",
            "tok_date", "tok_timestamp"};
    for (String f : castFunctions) {
      FUNCTION_MAP.put(f, HiveFunction.CAST);
    }
  }

  /**
   * Returns a predicate to test if ordinal parameter is optional
   * @param ordinal parameter ordinal number
   * @return predicate to test if the parameter is optional
   */
  private static PredicateImpl<Integer> optionalOrd(final int ordinal) {
    return new PredicateImpl<Integer>() {
      @Override
      public boolean test(Integer input) {
        return input == ordinal;
      }
    };
  }

  private static PredicateImpl<Integer> optionalOrd(final List<Integer> ordinals) {
    return new PredicateImpl<Integer>() {
      @Override
      public boolean test(Integer input) {
        return ordinals.contains(input);
      }
    };
  }
}
