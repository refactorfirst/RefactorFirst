/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.cli.console;

import static io.confluent.ksql.util.CmdLineUtil.splitByUnquotedWhitespace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import io.confluent.ksql.cli.console.CliConfig.OnOff;
import io.confluent.ksql.cli.console.KsqlTerminal.HistoryEntry;
import io.confluent.ksql.cli.console.KsqlTerminal.StatusClosable;
import io.confluent.ksql.cli.console.cmd.CliSpecificCommand;
import io.confluent.ksql.cli.console.table.Table;
import io.confluent.ksql.cli.console.table.Table.Builder;
import io.confluent.ksql.cli.console.table.builder.CommandStatusTableBuilder;
import io.confluent.ksql.cli.console.table.builder.ConnectorInfoTableBuilder;
import io.confluent.ksql.cli.console.table.builder.ConnectorListTableBuilder;
import io.confluent.ksql.cli.console.table.builder.ConnectorPluginsListTableBuilder;
import io.confluent.ksql.cli.console.table.builder.DropConnectorTableBuilder;
import io.confluent.ksql.cli.console.table.builder.ExecutionPlanTableBuilder;
import io.confluent.ksql.cli.console.table.builder.FunctionNameListTableBuilder;
import io.confluent.ksql.cli.console.table.builder.KafkaTopicsListTableBuilder;
import io.confluent.ksql.cli.console.table.builder.ListVariablesTableBuilder;
import io.confluent.ksql.cli.console.table.builder.PropertiesListTableBuilder;
import io.confluent.ksql.cli.console.table.builder.QueriesTableBuilder;
import io.confluent.ksql.cli.console.table.builder.StreamsListTableBuilder;
import io.confluent.ksql.cli.console.table.builder.TableBuilder;
import io.confluent.ksql.cli.console.table.builder.TablesListTableBuilder;
import io.confluent.ksql.cli.console.table.builder.TerminateQueryTableBuilder;
import io.confluent.ksql.cli.console.table.builder.TopicDescriptionTableBuilder;
import io.confluent.ksql.cli.console.table.builder.TypeListTableBuilder;
import io.confluent.ksql.cli.console.table.builder.WarningEntityTableBuilder;
import io.confluent.ksql.metrics.TopicSensors.Stat;
import io.confluent.ksql.model.WindowType;
import io.confluent.ksql.query.QueryError;
import io.confluent.ksql.rest.ApiJsonMapper;
import io.confluent.ksql.rest.entity.ArgumentInfo;
import io.confluent.ksql.rest.entity.AssertSchemaEntity;
import io.confluent.ksql.rest.entity.AssertTopicEntity;
import io.confluent.ksql.rest.entity.CommandStatusEntity;
import io.confluent.ksql.rest.entity.ConnectorDescription;
import io.confluent.ksql.rest.entity.ConnectorList;
import io.confluent.ksql.rest.entity.ConnectorPluginsList;
import io.confluent.ksql.rest.entity.CreateConnectorEntity;
import io.confluent.ksql.rest.entity.DropConnectorEntity;
import io.confluent.ksql.rest.entity.ExecutionPlan;
import io.confluent.ksql.rest.entity.FieldInfo;
import io.confluent.ksql.rest.entity.FieldInfo.FieldType;
import io.confluent.ksql.rest.entity.FunctionDescriptionList;
import io.confluent.ksql.rest.entity.FunctionInfo;
import io.confluent.ksql.rest.entity.FunctionNameList;
import io.confluent.ksql.rest.entity.KafkaTopicsList;
import io.confluent.ksql.rest.entity.KafkaTopicsListExtended;
import io.confluent.ksql.rest.entity.KsqlEntity;
import io.confluent.ksql.rest.entity.KsqlErrorMessage;
import io.confluent.ksql.rest.entity.KsqlStatementErrorMessage;
import io.confluent.ksql.rest.entity.KsqlWarning;
import io.confluent.ksql.rest.entity.PropertiesList;
import io.confluent.ksql.rest.entity.Queries;
import io.confluent.ksql.rest.entity.QueryDescription;
import io.confluent.ksql.rest.entity.QueryDescriptionEntity;
import io.confluent.ksql.rest.entity.QueryDescriptionList;
import io.confluent.ksql.rest.entity.QueryHostStat;
import io.confluent.ksql.rest.entity.QueryOffsetSummary;
import io.confluent.ksql.rest.entity.QueryTopicOffsetSummary;
import io.confluent.ksql.rest.entity.RunningQuery;
import io.confluent.ksql.rest.entity.SourceDescription;
import io.confluent.ksql.rest.entity.SourceDescriptionEntity;
import io.confluent.ksql.rest.entity.SourceDescriptionList;
import io.confluent.ksql.rest.entity.StreamedRow;
import io.confluent.ksql.rest.entity.StreamedRow.DataRow;
import io.confluent.ksql.rest.entity.StreamedRow.Header;
import io.confluent.ksql.rest.entity.StreamsList;
import io.confluent.ksql.rest.entity.TablesList;
import io.confluent.ksql.rest.entity.TerminateQueryEntity;
import io.confluent.ksql.rest.entity.TopicDescription;
import io.confluent.ksql.rest.entity.TypeList;
import io.confluent.ksql.rest.entity.VariablesList;
import io.confluent.ksql.rest.entity.WarningEntity;
import io.confluent.ksql.util.CmdLineUtil;
import io.confluent.ksql.util.HandlerMaps;
import io.confluent.ksql.util.HandlerMaps.ClassHandlerMap1;
import io.confluent.ksql.util.HandlerMaps.Handler1;
import io.confluent.ksql.util.KsqlException;
import io.confluent.ksql.util.TabularRow;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorStateInfo;
import org.jline.terminal.Terminal.Signal;
import org.jline.terminal.Terminal.SignalHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// CHECKSTYLE_RULES.OFF: ClassDataAbstractionCoupling
public class Console implements Closeable {
    // CHECKSTYLE_RULES.ON: ClassDataAbstractionCoupling

    private static final Logger log = LoggerFactory.getLogger(Console.class);
    private static final ObjectMapper OBJECT_MAPPER = ApiJsonMapper.INSTANCE.get();

    private static final ClassHandlerMap1<KsqlEntity, Console> PRINT_HANDLERS =
            HandlerMaps.forClass(KsqlEntity.class).withArgType(Console.class)
                    .put(CommandStatusEntity.class,
                            tablePrinter(CommandStatusEntity.class, CommandStatusTableBuilder::new))
                    .put(PropertiesList.class,
                            tablePrinter(PropertiesList.class, PropertiesListTableBuilder::new))
                    .put(Queries.class,
                            tablePrinter(Queries.class, QueriesTableBuilder::new))
                    .put(SourceDescriptionEntity.class,
                            (console, entity) -> console.printSourceDescription(entity.getSourceDescription()))
                    .put(SourceDescriptionList.class,
                            Console::printSourceDescriptionList)
                    .put(QueryDescriptionEntity.class,
                            (console, entity) -> console.printQueryDescription(entity.getQueryDescription()))
                    .put(QueryDescriptionList.class,
                            Console::printQueryDescriptionList)
                    .put(TopicDescription.class,
                            tablePrinter(TopicDescription.class, TopicDescriptionTableBuilder::new))
                    .put(StreamsList.class,
                            tablePrinter(StreamsList.class, StreamsListTableBuilder::new))
                    .put(TablesList.class,
                            tablePrinter(TablesList.class, TablesListTableBuilder::new))
                    .put(KafkaTopicsList.class,
                            tablePrinter(KafkaTopicsList.class, KafkaTopicsListTableBuilder.SimpleBuilder::new))
                    .put(KafkaTopicsListExtended.class,
                            tablePrinter(
                                    KafkaTopicsListExtended.class,
                                    KafkaTopicsListTableBuilder.ExtendedBuilder::new))
                    .put(ExecutionPlan.class,
                            tablePrinter(ExecutionPlan.class, ExecutionPlanTableBuilder::new))
                    .put(FunctionNameList.class,
                            tablePrinter(FunctionNameList.class, FunctionNameListTableBuilder::new))
                    .put(FunctionDescriptionList.class,
                            Console::printFunctionDescription)
                    .put(CreateConnectorEntity.class,
                            tablePrinter(CreateConnectorEntity.class, ConnectorInfoTableBuilder::new))
                    .put(DropConnectorEntity.class,
                            tablePrinter(DropConnectorEntity.class, DropConnectorTableBuilder::new))
                    .put(ConnectorList.class,
                            tablePrinter(ConnectorList.class, ConnectorListTableBuilder::new))
                    .put(ConnectorPluginsList.class,
                            tablePrinter(ConnectorPluginsList.class, ConnectorPluginsListTableBuilder::new))
                    .put(ConnectorDescription.class,
                            Console::printConnectorDescription)
                    .put(TypeList.class,
                            tablePrinter(TypeList.class, TypeListTableBuilder::new))
                    .put(WarningEntity.class,
                            tablePrinter(WarningEntity.class, WarningEntityTableBuilder::new))
                    .put(VariablesList.class,
                            tablePrinter(VariablesList.class, ListVariablesTableBuilder::new))
                    .put(TerminateQueryEntity.class,
                            tablePrinter(TerminateQueryEntity.class, TerminateQueryTableBuilder::new))
                    .put(AssertTopicEntity.class, Console::printAssertTopic)
                    .put(AssertSchemaEntity.class, Console::printAssertSchema)
                    .build();

    private static <T extends KsqlEntity> Handler1<KsqlEntity, Console> tablePrinter(
            final Class<T> entityType,
            final Supplier<? extends TableBuilder<T>> tableBuilderType) {

        try {
            final TableBuilder<T> tableBuilder = tableBuilderType.get();

            return (console, type) -> {
                final Table table = tableBuilder.buildTable(entityType.cast(type));
                table.print(console);
            };
        } catch (final Exception e) {
            throw new IllegalStateException("Error instantiating tableBuilder: " + tableBuilderType);
        }
    }

    private final Map<String, CliSpecificCommand> cliSpecificCommands;
    private final KsqlTerminal terminal;
    private final RowCaptor rowCaptor;
    private OutputFormat outputFormat;
    private Optional<File> spoolFile = Optional.empty();
    private CliConfig config;

    public interface RowCaptor {

        void addRow(DataRow row);

        void addRows(List<List<String>> fields);
    }

    public static Console build(final OutputFormat outputFormat) {
        final AtomicReference<Console> consoleRef = new AtomicReference<>();
        final Predicate<String> isCliCommand = line -> {
            final Console theConsole = consoleRef.get();
            return theConsole != null && theConsole.getCliCommand(line).isPresent();
        };

        final Path historyFilePath = Paths.get(System.getProperty(
                "history-file",
                System.getProperty("user.home")
                        + "/.ksql-history"
        )).toAbsolutePath();

        final KsqlTerminal terminal = new JLineTerminal(isCliCommand, historyFilePath);

        final Console console = new Console(
                outputFormat, terminal, new NoOpRowCaptor());

        consoleRef.set(console);
        return console;
    }

    public Console(
            final OutputFormat outputFormat,
            final KsqlTerminal terminal,
            final RowCaptor rowCaptor
    ) {
        this.outputFormat = Objects.requireNonNull(outputFormat, "outputFormat");
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.rowCaptor = Objects.requireNonNull(rowCaptor, "rowCaptor");
        this.cliSpecificCommands = Maps.newLinkedHashMap();
        this.config = new CliConfig(ImmutableMap.of());
    }

    public PrintWriter writer() {
        return terminal.writer();
    }

    public void flush() {
        terminal.flush();
    }

    public void setSpool(final File file) {
        try {
            terminal.setSpool(new PrintWriter(file, Charset.defaultCharset().name()));
            spoolFile = Optional.of(file);
            terminal.writer().println("Session will be spooled to " + file.getAbsolutePath());
            terminal.writer().println("Enter SPOOL OFF to disable");
        } catch (final IOException e) {
            throw new KsqlException("Cannot SPOOL to file: " + file, e);
        }
    }

    public void unsetSpool() {
        terminal.unsetSpool();
        spoolFile.ifPresent(f -> terminal.writer().println("Spool written to " + f.getAbsolutePath()));
        spoolFile = Optional.empty();
    }

    public int getWidth() {
        return terminal.getWidth();
    }

    public void clearScreen() {
        terminal.clearScreen();
    }

    public StatusClosable setStatusMessage(final String message) {
        return terminal.setStatusMessage(message);
    }

    public void handle(final Signal signal, final SignalHandler signalHandler) {
        terminal.handle(signal, signalHandler);
    }

    public void setCliProperty(final String name, final Object value) {
        try {
            config = config.with(name, value);
        } catch (final ConfigException e) {
            terminal.writer().println(e.getMessage());
        }
    }

    @Override
    public void close() {
        terminal.close();
    }

    public void addResult(final List<List<String>> rowValues) {
        rowCaptor.addRows(rowValues);
    }

    public Map<String, CliSpecificCommand> getCliSpecificCommands() {
        return new HashMap<>(cliSpecificCommands);
    }

    public String nextNonCliCommand() {
        String line;

        do {
            line = terminal.readLine();

        } while (maybeHandleCliSpecificCommands(line));

        return line;
    }

    public List<HistoryEntry> getHistory() {
        return Collections.unmodifiableList(terminal.getHistory());
    }

    public void printErrorMessage(final KsqlErrorMessage errorMessage) {
        if (errorMessage instanceof KsqlStatementErrorMessage) {
            printKsqlEntityList(((KsqlStatementErrorMessage) errorMessage).getEntities());
        }
        printError(errorMessage.getMessage(), errorMessage.toString());
    }

    public void printError(final String shortMsg, final String fullMsg) {
        log.error(fullMsg);
        terminal.printError(shortMsg);
    }

    public void printStreamedRow(final StreamedRow row) {
        row.getErrorMessage().ifPresent(this::printErrorMessage);

        row.getFinalMessage().ifPresent(finalMsg -> writer().println(finalMsg));

        row.getHeader().ifPresent(this::printRowHeader);

        if (row.getRow().isPresent()) {
            switch (outputFormat) {
                case JSON:
                    printAsJson(row.getRow().get());
                    break;
                case TABULAR:
                    printAsTable(row.getRow().get());
                    break;
                default:
                    throw new RuntimeException(String.format(
                            "Unexpected output format: '%s'",
                            outputFormat.name()
                    ));
            }
        }
    }

    public void printKsqlEntityList(final List<KsqlEntity> entityList) {
        switch (outputFormat) {
            case JSON:
                printAsJson(entityList);
                break;
            case TABULAR:
                final boolean showStatements = entityList.size() > 1;
                for (final KsqlEntity ksqlEntity : entityList) {
                    writer().println();
                    if (showStatements) {
                        writer().println(ksqlEntity.getStatementText());
                    }
                    printAsTable(ksqlEntity);
                }
                break;
            default:
                throw new RuntimeException(String.format(
                        "Unexpected output format: '%s'",
                        outputFormat.name()
                ));
        }
    }

    private void printRowHeader(final Header header) {
        switch (outputFormat) {
            case JSON:
                printAsJson(header);
                break;
            case TABULAR:
                writer().println(
                        TabularRow.createHeader(
                                getWidth(),
                                header.getSchema().columns(),
                                config.getString(CliConfig.WRAP_CONFIG).equalsIgnoreCase(OnOff.ON.toString()),
                                config.getInt(CliConfig.COLUMN_WIDTH_CONFIG)
                        )
                );
                break;
            default:
                throw new RuntimeException(String.format(
                        "Unexpected output format: '%s'",
                        outputFormat.name()
                ));
        }
    }

    public void registerCliSpecificCommand(final CliSpecificCommand cliSpecificCommand) {
        cliSpecificCommands.put(cliSpecificCommand.getName().toLowerCase(), cliSpecificCommand);
    }

    public void setOutputFormat(final String newFormat) {
        try {
            outputFormat = OutputFormat.get(newFormat);
            writer().printf("Output format set to %s%n", outputFormat.name());
        } catch (final IllegalArgumentException exception) {
            writer().printf(
                    "Invalid output format: '%s' (valid formats: %s)%n",
                    newFormat,
                    OutputFormat.VALID_FORMATS
            );
        }
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    private Optional<CliCmdExecutor> getCliCommand(final String line) {
        final List<String> parts = splitByUnquotedWhitespace(StringUtils.stripEnd(line.trim(), ";"));
        if (parts.isEmpty()) {
            return Optional.empty();
        }

        final String command = String.join(" ", parts);

        return cliSpecificCommands.values().stream()
                .filter(cliSpecificCommand -> cliSpecificCommand.matches(command))
                .map(cliSpecificCommand -> CliCmdExecutor.of(cliSpecificCommand, parts))
                .findFirst();
    }

    private void printAsTable(final DataRow row) {
        rowCaptor.addRow(row);

        final boolean tombstone = row.getTombstone().orElse(false);

        final List<?> columns = tombstone
                ? row.getColumns().stream()
                .map(val -> val == null ? "<TOMBSTONE>" : val)
                .collect(Collectors.toList())
                : row.getColumns();

        writer().println(TabularRow.createRow(
                getWidth(),
                columns,
                config.getString(CliConfig.WRAP_CONFIG).equalsIgnoreCase(OnOff.ON.toString()),
                config.getInt(CliConfig.COLUMN_WIDTH_CONFIG))
        );

        flush();
    }

    private void printAsTable(final KsqlEntity entity) {
        final Handler1<KsqlEntity, Console> handler = PRINT_HANDLERS.get(entity.getClass());

        if (handler == null) {
            throw new RuntimeException(String.format(
                    "Unexpected KsqlEntity class: '%s'", entity.getClass().getCanonicalName()
            ));
        }

        handler.handle(this, entity);

        printWarnings(entity);
    }

    private void printWarnings(final KsqlEntity entity) {
        for (final KsqlWarning warning : entity.getWarnings()) {
            writer().println("WARNING: " + warning.getMessage());
        }
    }

    private static String formatFieldType(
            final FieldInfo field,
            final Optional<WindowType> windowType,
            final boolean isTable
    ) {
        final FieldType possibleFieldType = field.getType().orElse(null);

        if (possibleFieldType == FieldType.HEADER) {
            final String headerType = field.getHeaderKey()
                    .map(k -> "(header('" + k + "'))")
                    .orElse("(headers)");
            return String.format("%-16s %s", field.getSchema().toTypeString(), headerType);
        }

        if (possibleFieldType == FieldType.KEY) {
            final String wt = windowType
                    .map(v -> " (Window type: " + v + ")")
                    .orElse("");

            final String keyType = isTable ? "(primary key)" : "(key)";
            return String.format("%-16s %s%s", field.getSchema().toTypeString(), keyType, wt);
        }

        return field.getSchema().toTypeString();
    }

    private void printSchema(
            final Optional<WindowType> windowType,
            final List<FieldInfo> fields,
            final boolean isTable
    ) {
        final Table.Builder tableBuilder = new Table.Builder();
        if (!fields.isEmpty()) {
            tableBuilder.withColumnHeaders("Field", "Type");
            fields.forEach(f -> tableBuilder.withRow(
                    f.getName(),
                    formatFieldType(f, windowType, isTable)
            ));
            tableBuilder.build().print(this);
        }
    }

    private void printTopicInfo(final SourceDescription source) {
        final String timestamp = source.getTimestamp().isEmpty()
                ? "Not set - using <ROWTIME>"
                : source.getTimestamp();

        writer().println(String.format("%-20s : %s", "Timestamp field", timestamp));
        writer().println(String.format("%-20s : %s", "Key format", source.getKeyFormat()));
        writer().println(String.format("%-20s : %s", "Value format", source.getValueFormat()));

        if (!source.getTopic().isEmpty()) {
            String topicInformation = String.format("%-20s : %s",
                    "Kafka topic",
                    source.getTopic()
            );

            // If Describe ACLs permissions aren't given for a topic, partitions and replica default to 0
            // Details aren't printed out if the Describe fails.
            if (source.getPartitions() != 0) {
                topicInformation = topicInformation.concat(String.format(
                        " (partitions: %d, replication: %d)",
                        source.getPartitions(),
                        source.getReplication()
                ));
            }
            writer().println(topicInformation);
        }
    }

    private void printSourceConstraints(final List<String> sourceConstraints) {
        if (!sourceConstraints.isEmpty()) {
            writer().println(String.format(
                    "%n%-20s%n%-20s",
                    "Sources that have a DROP constraint on this source",
                    "--------------------------------------------------"
            ));

            sourceConstraints.forEach(sourceName -> writer().println(sourceName));
        }
    }

    private void printQueries(
            final List<RunningQuery> queries,
            final String type,
            final String operation
    ) {
        if (!queries.isEmpty()) {
            writer().println(String.format(
                    "%n%-20s%n%-20s",
                    "Queries that " + operation + " from this " + type,
                    "-----------------------------------"
            ));
            for (final RunningQuery writeQuery : queries) {
                writer().println(writeQuery.getId()
                        + " (" + writeQuery.getState().orElse("N/A")
                        + ") : " + writeQuery.getQuerySingleLine());
            }
            writer().println("\nFor query topology and execution plan please run: EXPLAIN <QueryId>");
        }
    }

    private void printExecutionPlan(final QueryDescription queryDescription) {
        if (!queryDescription.getExecutionPlan().isEmpty()) {
            writer().println(String.format(
                    "%n%-20s%n%-20s%n%s",
                    "Execution plan",
                    "--------------",
                    queryDescription.getExecutionPlan()
            ));
        }
    }

    private void printTopology(final QueryDescription queryDescription) {
        if (!queryDescription.getTopology().isEmpty()) {
            writer().println(String.format(
                    "%n%-20s%n%-20s%n%s",
                    "Processing topology",
                    "-------------------",
                    queryDescription.getTopology()
            ));
        }
    }

    private void printOverriddenProperties(final QueryDescription queryDescription) {
        final Map<String, Object> overriddenProperties = queryDescription.getOverriddenProperties();
        if (overriddenProperties.isEmpty()) {
            return;
        }

        final List<List<String>> rows = overriddenProperties.entrySet().stream()
                .sorted(Entry.comparingByKey())
                .map(prop -> Arrays.asList(prop.getKey(), Objects.toString(prop.getValue())))
                .collect(Collectors.toList());

        new Builder()
                .withColumnHeaders("Property", "Value")
                .withRows(rows)
                .withHeaderLine(String.format(
                        "%n%-20s%n%-20s",
                        "Overridden Properties",
                        "---------------------"))
                .build()
                .print(this);
    }

    private void printQueryError(final QueryDescription query) {
        writer().println();

        final DateTimeFormatter dateFormatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss,SSS (z)");
        for (final QueryError error : query.getQueryErrors()) {
            final Instant ts = Instant.ofEpochMilli(error.getTimestamp());
            final String errorDate = ts.atZone(ZoneId.systemDefault()).format(dateFormatter);

            writer().println(String.format("%-20s : %s", "Error Date", errorDate));
            writer().println(String.format("%-20s : %s", "Error Details", error.getErrorMessage()));
            writer().println(String.format("%-20s : %s", "Error Type", error.getType()));
        }
    }

    private void printStatistics(final SourceDescription source) {
        final List<QueryHostStat> statistics = source.getClusterStatistics();
        final List<QueryHostStat> errors = source.getClusterErrorStats();

        if (statistics.isEmpty() && errors.isEmpty()) {
            writer().println(String.format(
                    "%n%-20s%n%s",
                    "Local runtime statistics",
                    "------------------------"
            ));
            writer().println(source.getStatistics());
            writer().println(source.getErrorStats());
            return;
        }
        final List<String> headers = ImmutableList.of("Host", "Metric", "Value", "Last Message");
        final Stream<QueryHostStat> rows = Streams.concat(statistics.stream(), errors.stream());

        writer().println(String.format(
                "%n%-20s%n%s",
                "Runtime statistics by host",
                "-------------------------"
        ));
        final Table statsTable = new Table.Builder()
                .withColumnHeaders(headers)
                .withRows(rows
                        .sorted(Comparator
                                .comparing(QueryHostStat::host)
                                .thenComparing(Stat::name)
                        )
                        .map((metric) -> {
                            final String hostCell = metric.host().toString();
                            final String formattedValue = String.format("%10.0f", metric.getValue());
                            return ImmutableList.of(hostCell, metric.name(), formattedValue, metric.timestamp());
                        }))
                .build();
        statsTable.print(this);
    }

    private void printSourceDescription(final SourceDescription source) {
        final boolean isTable = source.getType().equalsIgnoreCase("TABLE");

        writer().println(String.format("%-20s : %s", "Name", source.getName()));
        if (!source.isExtended()) {
            printSchema(source.getWindowType(), source.getFields(), isTable);
            writer().println(
                    "For runtime statistics and query details run: DESCRIBE <Stream,Table> EXTENDED;");
            return;
        }
        writer().println(String.format("%-20s : %s", "Type", source.getType()));

        printTopicInfo(source);
        writer().println(String.format("%-20s : %s", "Statement", source.getStatement()));
        writer().println("");

        printSchema(source.getWindowType(), source.getFields(), isTable);

        printSourceConstraints(source.getSourceConstraints());

        printQueries(source.getReadQueries(), source.getType(), "read");

        printQueries(source.getWriteQueries(), source.getType(), "write");
        printStatistics(source);

        writer().println(String.format(
                "(%s)",
                "Statistics of the local KSQL server interaction with the Kafka topic "
                        + source.getTopic()
        ));
        if (!source.getQueryOffsetSummaries().isEmpty()) {
            writer().println();
            writer().println("Consumer Groups summary:");
            for (QueryOffsetSummary entry : source.getQueryOffsetSummaries()) {
                writer().println();
                writer().println(String.format("%-20s : %s", "Consumer Group", entry.getGroupId()));
                if (entry.getTopicSummaries().isEmpty()) {
                    writer().println("<no offsets committed by this group yet>");
                }
                for (QueryTopicOffsetSummary topicSummary : entry.getTopicSummaries()) {
                    writer().println();
                    writer().println(String.format("%-20s : %s",
                            "Kafka topic", topicSummary.getKafkaTopic()));
                    writer().println(String.format("%-20s : %s",
                            "Max lag", topicSummary.getOffsets().stream()
                                    .mapToLong(s -> s.getLogEndOffset() - s.getConsumerOffset())
                                    .max()
                                    .orElse(0)));
                    writer().println("");
                    final Table taskTable = new Table.Builder()
                            .withColumnHeaders(
                                    ImmutableList.of("Partition", "Start Offset", "End Offset", "Offset", "Lag"))
                            .withRows(topicSummary.getOffsets()
                                    .stream()
                                    .map(offset -> ImmutableList.of(
                                            String.valueOf(offset.getPartition()),
                                            String.valueOf(offset.getLogStartOffset()),
                                            String.valueOf(offset.getLogEndOffset()),
                                            String.valueOf(offset.getConsumerOffset()),
                                            String.valueOf(offset.getLogEndOffset() - offset.getConsumerOffset())
                                    )))
                            .build();
                    taskTable.print(this);
                }
            }
        }
    }

    private void printSourceDescriptionList(final SourceDescriptionList sourceDescriptionList) {
        sourceDescriptionList.getSourceDescriptions().forEach(
                sourceDescription -> {
                    printSourceDescription(sourceDescription);
                    writer().println();
                });
    }

    private void printQuerySources(final QueryDescription query) {
        if (!query.getSources().isEmpty()) {
            writer().println(String.format(
                    "%n%-20s%n%-20s",
                    "Sources that this query reads from: ",
                    "-----------------------------------"
            ));
            for (final String sources : query.getSources()) {
                writer().println(sources);
            }
            writer().println("\nFor source description please run: DESCRIBE [EXTENDED] <SourceId>");
        }
    }

    private void printQuerySinks(final QueryDescription query) {
        if (!query.getSinks().isEmpty()) {
            writer().println(String.format(
                    "%n%-20s%n%-20s",
                    "Sinks that this query writes to: ",
                    "-----------------------------------"
            ));
            for (final String sinks : query.getSinks()) {
                writer().println(sinks);
            }
            writer().println("\nFor sink description please run: DESCRIBE [EXTENDED] <SinkId>");
        }
    }

    private void printQueryDescription(final QueryDescription query) {
        writer().println(String.format("%-20s : %s", "ID", query.getId()));
        writer().println(String.format("%-20s : %s", "Query Type", query.getQueryType()));
        if (query.getStatementText().length() > 0) {
            writer().println(String.format("%-20s : %s", "SQL", query.getStatementText()));
        }
        if (!query.getKsqlHostQueryStatus().isEmpty()) {
            writer().println(String.format(
                    "%-20s : %s", "Host Query Status",
                    query.getKsqlHostQueryStatus()));
        }
        writer().println();
        printSchema(query.getWindowType(), query.getFields(), false);
        printQuerySources(query);
        printQuerySinks(query);
        printExecutionPlan(query);
        printTopology(query);
        printOverriddenProperties(query);
        printQueryError(query);
    }

    private void printConnectorDescription(final ConnectorDescription description) {
        final ConnectorStateInfo status = description.getStatus();
        writer().println(String.format("%-20s : %s", "Name", status.name()));
        writer().println(String.format("%-20s : %s", "Class", description.getConnectorClass()));
        writer().println(String.format("%-20s : %s", "Type", description.getStatus().type()));
        writer().println(String.format("%-20s : %s", "State", status.connector().state()));
        writer().println(String.format("%-20s : %s", "WorkerId", status.connector().workerId()));
        if (!ObjectUtils.defaultIfNull(status.connector().trace(), "").isEmpty()) {
            writer().println(String.format("%-20s : %s", "Trace", status.connector().trace()));
        }

        if (!status.tasks().isEmpty()) {
            writer().println();
            final Table taskTable = new Table.Builder()
                    .withColumnHeaders(ImmutableList.of("Task ID", "State", "Error Trace"))
                    .withRows(status.tasks()
                            .stream()
                            .map(task -> ImmutableList.of(
                                    String.valueOf(task.id()),
                                    task.state(),
                                    ObjectUtils.defaultIfNull(task.trace(), ""))))
                    .build();
            taskTable.print(this);
        }

        if (!description.getSources().isEmpty()) {
            writer().println();
            final Table sourceTable = new Table.Builder()
                    .withColumnHeaders("KSQL Source Name", "Kafka Topic", "Type")
                    .withRows(description.getSources()
                            .stream()
                            .map(source -> ImmutableList
                                    .of(source.getName(), source.getTopic(), source.getType())))
                    .build();
            sourceTable.print(this);
        }

        if (!description.getTopics().isEmpty()) {
            writer().println();
            final Table topicTable = new Table.Builder()
                    .withColumnHeaders("Related Topics")
                    .withRows(description.getTopics().stream().map(ImmutableList::of))
                    .build();
            topicTable.print(this);
        }
    }

    private void printQueryDescriptionList(final QueryDescriptionList queryDescriptionList) {
        queryDescriptionList.getQueryDescriptions().forEach(
                queryDescription -> {
                    printQueryDescription(queryDescription);
                    writer().println();
                });
    }

    private void printFunctionDescription(final FunctionDescriptionList describeFunction) {
        final String functionName = describeFunction.getName().toUpperCase();
        final String baseFormat = "%-12s: %s%n";
        final String subFormat = "\t%-12s: %s%n";
        writer().printf(baseFormat, "Name", functionName);
        if (!describeFunction.getAuthor().trim().isEmpty()) {
            writer().printf(baseFormat, "Author", describeFunction.getAuthor());
        }
        if (!describeFunction.getVersion().trim().isEmpty()) {
            writer().printf(baseFormat, "Version", describeFunction.getVersion());
        }

        printDescription(baseFormat, "Overview", describeFunction.getDescription());

        writer().printf(baseFormat, "Type", describeFunction.getType().name());
        writer().printf(baseFormat, "Jar", describeFunction.getPath());
        writer().printf(baseFormat, "Variations", "");
        final Collection<FunctionInfo> functions = describeFunction.getFunctions();
        functions.forEach(functionInfo -> {
                    final String arguments = functionInfo.getArguments().stream()
                            .map(Console::argToString)
                            .collect(Collectors.joining(", "));

                    writer().printf("%n\t%-12s: %s(%s)%n", "Variation", functionName, arguments);

                    writer().printf(subFormat, "Returns", functionInfo.getReturnType());
                    printDescription(subFormat, "Description", functionInfo.getDescription());
                    functionInfo.getArguments()
                            .forEach(a -> printDescription(subFormat, a.getName(), a.getDescription()));
                }
        );
    }

    private void printAssertTopic(final AssertTopicEntity assertTopic) {
        final String existence = assertTopic.getExists() ? " exists" : " does not exist";
        writer().printf("Topic " + assertTopic.getTopicName() + existence + ".\n");
    }

    private void printAssertSchema(final AssertSchemaEntity assertSchema) {
        if (!assertSchema.getId().isPresent() && !assertSchema.getSubject().isPresent()) {
            throw new RuntimeException("No subject or id found in AssertSchema response.");
        }

        final String existence = assertSchema.getExists() ? " exists" : " does not exist";
        final String subject = assertSchema.getSubject().isPresent()
                ? " subject " + assertSchema.getSubject().get()
                : "";
        final String id = assertSchema.getId().isPresent()
                ? " id " + assertSchema.getId().get()
                : "";
        writer().printf("Schema with" + subject + id + existence + ".\n");
    }

    private static String argToString(final ArgumentInfo arg) {
        final String type = arg.getType() + (arg.getIsVariadic() ? "[]" : "");
        return arg.getName().isEmpty() ? type : (arg.getName() + " " + type);
    }

    private void printDescription(final String format, final String name, final String description) {
        final String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        final int labelLen = String.format(format.replace("%n", ""), name, "")
                .replace("\t", "  ")
                .length();

        final int width = Math.max(getWidth(), 80) - labelLen;

        final String fixedWidth = splitLongLine(trimmed, width);

        final String indent = String.format("%-" + labelLen + "s", "");

        final String result = fixedWidth
                .replace(System.lineSeparator(), System.lineSeparator() + indent);

        writer().printf(format, name, result);
    }

    private static String splitLongLine(final String input, final int maxLineLength) {
        final StringTokenizer spaceTok = new StringTokenizer(input, " \n", true);
        final StringBuilder output = new StringBuilder(input.length());
        int lineLen = 0;
        while (spaceTok.hasMoreTokens()) {
            final String word = spaceTok.nextToken();
            final boolean isNewLineChar = word.equals("\n");

            if (isNewLineChar || lineLen + word.length() > maxLineLength) {
                output.append(System.lineSeparator());
                lineLen = 0;

                if (isNewLineChar) {
                    continue;
                }
            }

            output.append(word);
            lineLen += word.length();
        }
        return output.toString();
    }

    private void printAsJson(final Object o) {
        try {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(writer(), o);
            writer().println();
            flush();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to write to console", e);
        }
    }

    static class NoOpRowCaptor implements RowCaptor {

        @Override
        public void addRow(final DataRow row) {
        }

        @Override
        public void addRows(final List<List<String>> fields) {
        }
    }

    public boolean maybeHandleCliSpecificCommands(final String line) {
        if (line == null) {
            return false;
        }

        return getCliCommand(line)
                .map(cmd -> {
                    cmd.execute(writer());
                    flush();
                    return true;
                })
                .orElse(false);
    }

    private static final class CliCmdExecutor {

        private final CliSpecificCommand cmd;
        private final List<String> args;


        private static CliCmdExecutor of(final CliSpecificCommand cmd, final List<String> lineParts) {
            final String[] nameParts = cmd.getName().split("\\s+");
            final List<String> argList = lineParts.subList(nameParts.length, lineParts.size()).stream()
                    .map(CmdLineUtil::removeMatchedSingleQuotes)
                    .collect(Collectors.toList());

            return new CliCmdExecutor(cmd, argList);
        }

        private CliCmdExecutor(final CliSpecificCommand cmd, final List<String> args) {
            this.cmd = Objects.requireNonNull(cmd, "cmd");
            this.args = ImmutableList.copyOf(Objects.requireNonNull(args, "args"));
        }

        public void execute(final PrintWriter terminal) {
            cmd.execute(args, terminal);
        }
    }
}
