package com.xuxueli.poi.excel;

import com.xuxueli.poi.excel.annotation.ExcelField;
import com.xuxueli.poi.excel.annotation.ExcelIgnore;
import com.xuxueli.poi.excel.annotation.ExcelSheet;
import com.xuxueli.poi.excel.util.FieldReflectionUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel导出工具
 *
 * @author xuxueli 2017-09-08 22:27:20
 */
public class ExcelExportUtil {
    private static Logger logger = LoggerFactory.getLogger(ExcelExportUtil.class);


    /**
     * 导出Excel对象
     *
     * @param dataListArr  Excel数据
     * @return
     */
    public static Workbook exportWorkbook(List<?>... dataListArr){

        // data array valid
        if (dataListArr==null || dataListArr.length==0) {
            throw new RuntimeException(">>>>>>>>>>> xxl-excel error, data array can not be empty.");
        }

        // book
        Workbook workbook = new HSSFWorkbook();     // HSSFWorkbook=2003/xls、XSSFWorkbook=2007/xlsx

        // sheet
        for (List<?> dataList: dataListArr) {
            makeSheet(workbook, dataList);
        }

        return workbook;
    }

    private static void makeSheet(Workbook workbook, List<?> dataList){
        // data
        if (dataList==null || dataList.size()==0) {
            throw new RuntimeException(">>>>>>>>>>> xxl-excel error, data can not be empty.");
        }

        // sheet
        Class<?> sheetClass = dataList.get(0).getClass();
        ExcelSheet excelSheet = sheetClass.getAnnotation(ExcelSheet.class);

        String sheetName = dataList.get(0).getClass().getSimpleName();
        HSSFColor.HSSFColorPredefined headColor = null;
        if (excelSheet != null) {
            if (excelSheet.name()!=null && excelSheet.name().trim().length()>0) {
                sheetName = excelSheet.name().trim();
            }
            headColor = excelSheet.headColor();
        }

        Sheet existSheet = workbook.getSheet(sheetName);
        if (existSheet != null) {
            for (int i = 2; i <= 1000; i++) {
                String newSheetName = sheetName.concat(String.valueOf(i));  // avoid sheetName repetition
                existSheet = workbook.getSheet(newSheetName);
                if (existSheet == null) {
                    sheetName = newSheetName;
                    break;
                } else {
                    continue;
                }
            }
        }

        Sheet sheet = workbook.createSheet(sheetName);

        // sheet field
        List<Field> fields = new ArrayList<Field>();
        if (sheetClass.getDeclaredFields()!=null && sheetClass.getDeclaredFields().length>0) {
            for (Field field: sheetClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                if (field.getAnnotation(ExcelIgnore.class) != null){
                    continue;
                }

                fields.add(field);
            }
        }

        if (fields==null || fields.size()==0) {
            throw new RuntimeException(">>>>>>>>>>> xxl-excel error, data field can not be empty.");
        }

        // sheet header row
        CellStyle[] fieldDataStyleArr = new CellStyle[fields.size()];
        int[] fieldWidthArr = new int[fields.size()];
        Row headRow = sheet.createRow(0);
        for (int i = 0; i < fields.size(); i++) {

            // field
            Field field = fields.get(i);
            ExcelField excelField = field.getAnnotation(ExcelField.class);

            String fieldName = field.getName();
            int fieldWidth = 0;
            HorizontalAlignment align = null;
            if (excelField != null) {
                if (excelField.name()!=null && excelField.name().trim().length()>0) {
                    fieldName = excelField.name().trim();
                }
                fieldWidth = excelField.width();
                align = excelField.align();
            }

            // field width
            fieldWidthArr[i] = fieldWidth;

            // head-style、field-data-style
            CellStyle fieldDataStyle = workbook.createCellStyle();
            if (align != null) {
                fieldDataStyle.setAlignment(align);
            }
            fieldDataStyleArr[i] = fieldDataStyle;

            CellStyle headStyle = workbook.createCellStyle();
            headStyle.cloneStyleFrom(fieldDataStyle);
            if (headColor != null) {
                headStyle.setFillForegroundColor(headColor.getIndex());
                headStyle.setFillBackgroundColor(headColor.getIndex());
                headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }

            // head-field data
            Cell cellX = headRow.createCell(i, CellType.STRING);
            cellX.setCellStyle(headStyle);
            cellX.setCellValue(String.valueOf(fieldName));
        }

        // sheet data rows
        for (int dataIndex = 0; dataIndex < dataList.size(); dataIndex++) {
            int rowIndex = dataIndex+1;
            Object rowData = dataList.get(dataIndex);

            Row rowX = sheet.createRow(rowIndex);

            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(rowData);

                    String fieldValueString = FieldReflectionUtil.formatValue(field, fieldValue);

                    Cell cellX = rowX.createCell(i, CellType.STRING);
                    cellX.setCellValue(fieldValueString);
                    cellX.setCellStyle(fieldDataStyleArr[i]);
                } catch (IllegalAccessException e) {
                    logger.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }
        }

        // sheet finally
        for (int i = 0; i < fields.size(); i++) {
            int fieldWidth = fieldWidthArr[i];
            if (fieldWidth > 0) {
                sheet.setColumnWidth(i, fieldWidth);
            } else {
                sheet.autoSizeColumn((short)i);
            }
        }
    }

    /**
     * 导出Excel文件到磁盘
     *
     * @param filePath
     * @param dataList
     */
    public static void exportToFile(String filePath, List<?>... dataList){
        // workbook
        Workbook workbook = exportWorkbook(dataList);

        FileOutputStream fileOutputStream = null;
        try {
            // workbook 2 FileOutputStream
            fileOutputStream = new FileOutputStream(filePath);
            workbook.write(fileOutputStream);

            // flush
            fileOutputStream.flush();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            try {
                if (fileOutputStream!=null) {
                    fileOutputStream.close();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 导出Excel字节数据
     *
     * @param dataList
     * @return
     */
    public static byte[] exportToBytes(List<?>... dataList){
        // workbook
        Workbook workbook = exportWorkbook(dataList);

        ByteArrayOutputStream byteArrayOutputStream = null;
        byte[] result = null;
        try {
            // workbook 2 ByteArrayOutputStream
            byteArrayOutputStream = new ByteArrayOutputStream();
            workbook.write(byteArrayOutputStream);

            // flush
            byteArrayOutputStream.flush();

            result = byteArrayOutputStream.toByteArray();
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            try {
                if (byteArrayOutputStream != null) {
                    byteArrayOutputStream.close();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

}
