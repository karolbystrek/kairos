"use client";

/*
 * Adapted from shadcn/ui chart; uses Kairos theme tokens.
 *
 * MIT License
 *
 * Copyright (c) 2023 shadcn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import * as React from "react";
import { clsx as cn } from "clsx";
import * as RechartsPrimitive from "recharts";

export type ChartConfig = Record<
  string,
  { label: React.ReactNode; color: string }
>;

function ChartContainer({
  id,
  className,
  children,
  config,
  ...props
}: React.ComponentProps<"div"> & {
  config: ChartConfig;
  children: React.ComponentProps<
    typeof RechartsPrimitive.ResponsiveContainer
  >["children"];
}) {
  const uniqueId = React.useId();
  const chartId = `chart-${id ?? uniqueId.replace(/:/g, "")}`;

  return (
    <div
      className={cn(
        "flex min-w-0 justify-center text-xs [&_.recharts-cartesian-axis-tick_text]:fill-[var(--muted)] [&_.recharts-cartesian-grid_line]:stroke-separator [&_.recharts-surface]:outline-hidden [&_.recharts-surface:focus-visible]:outline-2 [&_.recharts-surface:focus-visible]:outline-offset-2 [&_.recharts-surface:focus-visible]:outline-foreground/50",
        className,
      )}
      data-chart={chartId}
      data-slot="chart"
      {...props}
    >
      <ChartStyle config={config} id={chartId} />
      <RechartsPrimitive.ResponsiveContainer
        initialDimension={{ width: 320, height: 200 }}
      >
        {children}
      </RechartsPrimitive.ResponsiveContainer>
    </div>
  );
}

const ChartStyle = ({ id, config }: { id: string; config: ChartConfig }) => {
  return (
    <style
      dangerouslySetInnerHTML={{
        __html: `[data-chart=${id}] {${Object.entries(config)
          .map(([key, item]) => `--color-${key}: ${item.color};`)
          .join("\n")}}`,
      }}
    />
  );
};

const ChartTooltip = RechartsPrimitive.Tooltip;

export { ChartContainer, ChartTooltip };
