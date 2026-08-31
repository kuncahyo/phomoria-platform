package com.phomoria.print;

import com.phomoria.config.AppSettings;
import com.phomoria.debug.DebugLog;

import java.awt.*;
import java.awt.print.*;
import java.awt.image.BufferedImage;
import javax.print.PrintService;

public final class PrinterService {

    public void testPrint(
            AppSettings settings
    ) throws PrinterException {
        PrintService[] services =
                PrinterJob.lookupPrintServices();

        DebugLog.info(
                "Printer count detected="
                        + services.length
        );

        PrinterJob job =
                PrinterJob.getPrinterJob();

        PageFormat format =
                createPageFormat(settings);

        job.setPrintable(
                (graphics, pageFormat, pageIndex) -> {
                    if (pageIndex > 0) {
                        return Printable.NO_SUCH_PAGE;
                    }

                    Graphics2D g =
                            (Graphics2D) graphics;

                    g.setColor(Color.WHITE);
                    g.fillRect(
                            0,
                            0,
                            (int) pageFormat.getWidth(),
                            (int) pageFormat.getHeight()
                    );

                    g.setColor(Color.BLACK);

                    g.setFont(
                            new Font(
                                    "SansSerif",
                                    Font.BOLD,
                                    24
                            )
                    );

                    g.drawString(
                            "PHOMORIA PRINT TEST",
                            30,
                            45
                    );

                    g.setFont(
                            new Font(
                                    "SansSerif",
                                    Font.PLAIN,
                                    14
                            )
                    );

                    g.drawString(
                            "Paper: "
                                    + settings
                                    .getPrintPaperSize()
                                    .getLabel(),
                            30,
                            75
                    );

                    g.drawString(
                            "DPI: "
                                    + settings
                                    .getPrintDpi(),
                            30,
                            98
                    );

                    g.drawRect(
                            20,
                            120,
                            (int) pageFormat.getImageableWidth()
                                    - 40,
                            (int) pageFormat.getImageableHeight()
                                    - 160
                    );

                    return Printable.PAGE_EXISTS;
                },
                format
        );

        DebugLog.info(
                "Sending printer test page."
        );

        job.print();

        DebugLog.info(
                "Printer test completed."
        );
    }

    private PageFormat createPageFormat(
            AppSettings settings
    ) {
        PageFormat format =
                PrinterJob.getPrinterJob()
                        .defaultPage();

        Paper paper =
                new Paper();

        double pointsPerInch = 72.0;

        double widthInches;
        double heightInches;

        if (settings.getPrintPaperSize().isCustom()) {
            widthInches =
                    settings.getCustomPaperWidthInches();

            heightInches =
                    settings.getCustomPaperHeightInches();
        } else {
            widthInches =
                    settings.getPrintPaperSize()
                            .getWidthInches();

            heightInches =
                    settings.getPrintPaperSize()
                            .getHeightInches();
        }

        double width =
                widthInches * pointsPerInch;

        double height =
                heightInches * pointsPerInch;

        paper.setSize(width, height);

        // Small physical margin. Printer driver may impose its own limits.
        double margin = 9.0;

        paper.setImageableArea(
                margin,
                margin,
                Math.max(1, width - margin * 2),
                Math.max(1, height - margin * 2)
        );

        format.setPaper(paper);

        return format;
    }
}
