import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";

/*
 * Android Auto icon generator
 *
 * Generates:
 *
 *   generated/svg/*.svg
 *   generated/drawable/*.xml
 *
 * The SVG is the human-readable intermediate representation. Labels are
 * emitted as custom vector paths so they remain independent of installed
 * fonts.
 */


/* -------------------------------------------------------------------------
 * Configuration
 * ---------------------------------------------------------------------- */

const CONFIG = {
  outputDir: "./generated",

  canvas: {
    width: 48,
    height: 48,
  },

  /*
   * All geometry below is in the 48x48 viewport.
   */
  skip: {
    center: {
      x: 24,
      y: 24,
    },

    /*
     * Angles:
     *
     *   0°   = 3 o'clock
     *   90°  = 6 o'clock
     *   180° = 9 o'clock
     *   270° = 12 o'clock
     *
     * Because SVG's Y axis points downwards, increasing angles travel
     * counter-clockwise visually.
     *
     * Thus 90 -> 150 is approximately:
     *
     *       12
     *    10
     *             3
     *             6
     *
     * i.e. back/rewind goes from 6 o'clock to 10 o'clock.
     */
    startAngle: 90,
    endAngle: 150,

    radius: 16,

    /*
     * The arrow itself is a stroked circular arc.
     */
    strokeWidth: 4,

    /*
     * Round line ends aren't particularly useful here because the
     * arrowhead covers the end, but round joins make the arrowhead nicer.
     */
    lineCap: "butt" as const,
    lineJoin: "round" as const,

    arrow: {
      /*
       * Distance from the arc endpoint backwards along the tangent.
       */
      length: 7,

      /*
       * Total width of the arrowhead.
       */
      width: 8,

      /*
       * Position of the arrowhead relative to the endpoint.
       *
       * 0 = exactly at the endpoint
       * positive = pulled backwards along the tangent
       */
      inset: 0,
    },

    number: {
      /*
       * Text is centered on this point.
       */
      centerX: 24,
      // Media3 places the label in the lower half of the arrow.  In the
      // extracted 960x960 vectors its optical bounds are y=400..640,
      // i.e. y=20..32 in our 48x48 viewport.
      centerY: 26,

      /*
       * Font units can make the apparent optical center slightly different
       * from the mathematical center. This lets you adjust it without
       * touching the arrow geometry.
       */
      offsetX: 0,
      offsetY: 0,

    },

    /*
     * Whether the number should be a cutout or a normal foreground shape.
     *
     * false is generally preferable for Android Auto because it gives us
     * one monochrome icon which the host can tint.
     *
     * With false, the arc and digits are all the same color.
     */
    numberCutout: false,
  },

  speed: {
    centerX: 24,
    centerY: 24,

    /*
     * Speedometer arc.
     */
    arc: {
      radius: 17,

      startAngle: 215,
      endAngle: 325,

      strokeWidth: 4,
    },

    /*
     * Small tick marks at the ends of the speedometer.
     */
    ticks: {
      enabled: false,
      length: 4,
      width: 4,
    },

    /*
     * Needle.
     */
    needle: {
      enabled: false,

      /*
       * Angle in the same coordinate system as above.
       * 270° is 12 o'clock.
       */
      angle: 270,

      length: 11,

      width: 3,
    },

    text: {
      centerX: 24,
      centerY: 27,

      offsetX: 0,
      offsetY: 0,
    },
  },

  /*
   * Number ranges.
   */
  values: {
    skipFrom: 1,
    skipTo: 60,
    skipStep: 1,

    speedFrom: 0.5,
    speedTo: 3.0,
    speedStep: 0.05,
  },
};


/* -------------------------------------------------------------------------
 * Types
 * ---------------------------------------------------------------------- */

type Point = {
  x: number;
  y: number;
};

type Shape = {
  svg: string;
  pathData?: string;
};

type GeneratedIcon = {
  name: string;
  svg: string;
  vectorDrawable: string;
};


/* -------------------------------------------------------------------------
 * Basic geometry
 * ---------------------------------------------------------------------- */

const rad = (degrees: number): number =>
  degrees * Math.PI / 180;

function pointOnCircle(
  center: Point,
  radius: number,
  angle: number,
): Point {
  const a = rad(angle);

  return {
    x: center.x + Math.cos(a) * radius,
    y: center.y + Math.sin(a) * radius,
  };
}

/*
 * Tangent for increasing angle.
 *
 * This is the direction travelled by our counter-clockwise SVG arc.
 */
function tangentAtAngle(angle: number): Point {
  const a = rad(angle);

  return {
    x: -Math.sin(a),
    y: Math.cos(a),
  };
}

function normalize(v: Point): Point {
  const length = Math.hypot(v.x, v.y);

  return {
    x: v.x / length,
    y: v.y / length,
  };
}

function perpendicular(v: Point): Point {
  return {
    x: -v.y,
    y: v.x,
  };
}

function add(a: Point, b: Point): Point {
  return {
    x: a.x + b.x,
    y: a.y + b.y,
  };
}

function subtract(a: Point, b: Point): Point {
  return {
    x: a.x - b.x,
    y: a.y - b.y,
  };
}

function multiply(v: Point, n: number): Point {
  return {
    x: v.x * n,
    y: v.y * n,
  };
}


/* -------------------------------------------------------------------------
 * SVG helpers
 * ---------------------------------------------------------------------- */

function fmt(n: number): string {
  /*
   * Avoid unnecessarily huge SVG/pathData numbers.
   */
  return Number(n.toFixed(4)).toString();
}

function point(p: Point): string {
  return `${fmt(p.x)},${fmt(p.y)}`;
}


/* -------------------------------------------------------------------------
 * Arc generation
 * ---------------------------------------------------------------------- */

function arcPath(
  center: Point,
  radius: number,
  startAngle: number,
  endAngle: number,
): string {
  const start = pointOnCircle(center, radius, startAngle);
  const end = pointOnCircle(center, radius, endAngle);

  const delta = endAngle - startAngle;

  /*
   * SVG's A command:
   *
   * A rx ry x-axis-rotation large-arc-flag sweep-flag x y
   *
   * Our direction is increasing angle, which is sweep=1 in SVG.
   */
  const largeArc = Math.abs(delta) > 180 ? 1 : 0;
  const sweep = delta >= 0 ? 1 : 0;

  return [
    `M ${point(start)}`,
    `A ${fmt(radius)} ${fmt(radius)} 0 ${largeArc} ${sweep} ${point(end)}`,
  ].join(" ");
}


/* -------------------------------------------------------------------------
 * Arrowhead
 * ---------------------------------------------------------------------- */

function arrowHead(
  center: Point,
  radius: number,
  angle: number,
  length: number,
  width: number,
  inset: number,
): string {
  const tip = pointOnCircle(center, radius, angle);

  /*
   * Tangent is the direction in which the arrow travels.
   */
  const tangent = normalize(tangentAtAngle(angle));

  /*
   * Pull the base backwards along the tangent.
   */
  const baseCenter = subtract(
    tip,
    multiply(tangent, length + inset),
  );

  const side = multiply(
    normalize(perpendicular(tangent)),
    width / 2,
  );

  const left = add(baseCenter, side);
  const right = subtract(baseCenter, side);

  return [
    `M ${point(tip)}`,
    `L ${point(left)}`,
    `L ${point(right)}`,
    "Z",
  ].join(" ");
}


/* -------------------------------------------------------------------------
 * Skip icon geometry
 * ---------------------------------------------------------------------- */

// Paths copied from the extracted Media3 vectors.  They are deliberately
// kept in the original 960-unit coordinate system so it is easy to compare
// them with the upstream XML; scalePathData converts them to our 48-unit
// SVG/VectorDrawable viewport.
const MEDIA3_SKIP_BACK_PATH =
  "M480,880Q405,880 339.5,851.5Q274,823 225.5,774.5Q177,726 148.5,660.5Q120,595 120,520L200,520Q200,637 281.5,718.5Q363,800 480,800Q597,800 678.5,718.5Q760,637 760,520Q760,403 678.5,321.5Q597,240 480,240L474,240L536,302L480,360L320,200L480,40L536,98L474,160L480,160Q555,160 620.5,188.5Q686,217 734.5,265.5Q783,314 811.5,379.5Q840,445 840,520Q840,595 811.5,660.5Q783,726 734.5,774.5Q686,823 620.5,851.5Q555,880 480,880Z";

const MEDIA3_SKIP_FORWARD_PATH =
  "M480,880Q405,880 339.5,851.5Q274,823 225.5,774.5Q177,726 148.5,660.5Q120,595 120,520Q120,445 148.5,379.5Q177,314 225.5,265.5Q274,217 339.5,188.5Q405,160 480,160L486,160L424,98L480,40L640,200L480,360L424,302L486,240L480,240Q363,240 281.5,321.5Q200,403 200,520Q200,637 281.5,718.5Q363,800 480,800Q597,800 678.5,718.5Q760,637 760,520L840,520Q840,595 811.5,660.5Q783,726 734.5,774.5Q686,823 620.5,851.5Q555,880 480,880Z";

function scalePathData(pathData: string, scale: number): string {
  return pathData.replace(/-?\d+(?:\.\d+)?/g, value =>
    fmt(Number(value) * scale),
  );
}

/*
 * Media3 does not use a font for the skip labels. These are the digit
 * outlines from its 5, 10 and 30 vectors, normalized to y=20..32.  The
 * remaining digits use the same 3px block thickness and 1px-ish rounded
 * joins, which keeps values that are not present in the extracted set in the
 * same visual family.
 */
type Media3Digit = {
  path: string;
  width: number;
};

const MEDIA3_DIGITS: Record<string, Media3Digit> = {
  "0": {
    width: 8,
    path: "M2,32Q1.15,32 0.575,31.425Q0,30.85 0,30L0,22Q0,21.15 0.575,20.575Q1.15,20 2,20L6,20Q6.85,20 7.425,20.575Q8,21.15 8,22L8,30Q8,30.85 7.425,31.425Q6.85,32 6,32L2,32ZM3,29L5,29Q5,29 5,29Q5,29 5,29L5,23Q5,23 5,23Q5,23 5,23L3,23Q3,23 3,23Q3,23 3,23L3,29Q3,29 3,29Q3,29 3,29Z",
  },
  "1": {
    width: 6,
    path: "M3,32L3,23L0,23L0,20L6,20L6,32L3,32Z",
  },
  "2": {
    width: 8,
    path: "M0,20L6,20Q8,20 8,22L8,24Q8,25 7,26L3,29L8,29L8,32L0,32L0,28Q0,27 1,26L5,23L5,23L0,23L0,20Z",
  },
  "3": {
    width: 8,
    path: "M0,32L0,29L5,29L5,27L2,27L2,25L5,25L5,23L0,23L0,20L6,20Q6.85,20 7.425,20.575Q8,21.15 8,22L8,30Q8,30.85 7.425,31.425Q6.85,32 6,32L0,32Z",
  },
  "4": {
    width: 8,
    path: "M5,32L5,30L0,30L0,27L4,20L8,20L8,27L9,27L9,30L8,30L8,32L5,32ZM3,27L5,27L5,23L3,27Z",
  },
  "5": {
    width: 9,
    path: "M0,32L0,29L6,29L6,27L0,27L0,20L9,20L9,23L3,23L3,25L7,25Q7.85,25 8.425,25.575Q9,26.15 9,27L9,30Q9,30.85 8.425,31.425Q7.85,32 7,32L0,32Z",
  },
  "6": {
    width: 8,
    path: "M2,20L8,20L8,23L3,23L3,25L6,25Q8,25 8,27L8,30Q8,32 6,32L2,32Q0,32 0,30L0,22Q0,20 2,20ZM3,28L3,29Q3,29 3,29L5,29Q5,29 5,29L5,28Q5,28 5,28L3,28Q3,28 3,28Z",
  },
  "7": {
    width: 8,
    path: "M0,20L8,20L8,23L4,32L0,32L4,23L0,23L0,20Z",
  },
  "8": {
    width: 8,
    path: "M2,20L6,20Q8,20 8,22L8,24Q8,25 7,26Q8,27 8,28L8,30Q8,32 6,32L2,32Q0,32 0,30L0,28Q0,27 1,26Q0,25 0,24L0,22Q0,20 2,20ZM3,23L3,24Q3,24 3,24L5,24Q5,24 5,24L5,23Q5,23 5,23L3,23Q3,23 3,23ZM3,28L3,29Q3,29 3,29L5,29Q5,29 5,29L5,28Q5,28 5,28L3,28Q3,28 3,28Z",
  },
  "9": {
    width: 8,
    path: "M2,20L6,20Q8,20 8,22L8,30Q8,32 6,32L0,32L0,29L5,29L5,27L2,27Q0,27 0,25L0,22Q0,20 2,20ZM3,23L3,24Q3,24 3,24L5,24Q5,24 5,24L5,23Q5,23 5,23L3,23Q3,23 3,23Z",
  },
  ".": {
    width: 3,
    path: "M0,29L3,29L3,32L0,32Z",
  },
  "x": {
    width: 8,
    // Lowercase x-height: unlike the digits, it does not reach the cap line.
    path: "M0,23L3,23L4,26L5,23L8,23L6,27.5L8,32L5,32L4,29L3,32L0,32L2,27.5L0,23Z",
  },
};

function media3LabelPath(
  text: string,
  centerX: number,
  gap: number,
): string {
  const widths = [...text].map(digit => MEDIA3_DIGITS[digit]?.width ?? 0);
  const totalWidth = widths.reduce((sum, width) => sum + width, 0) +
    gap * Math.max(0, text.length - 1);
  let x = centerX - totalWidth / 2;

  return [...text].map((digit, index) => {
    const glyph = MEDIA3_DIGITS[digit];
    if (!glyph) {
      throw new Error(`No Media3-style digit available for ${digit}`);
    }

    let coordinateIndex = 0;
    const translated = glyph.path.replace(/-?\d+(?:\.\d+)?/g, value => {
      const number = Number(value);
      // Coordinates alternate x/y in these hand-authored digit paths.
      return fmt(number + (coordinateIndex++ % 2 === 0 ? x : 0));
    });

    x += glyph.width + (index < text.length - 1 ? gap : 0);
    return translated;
  }).join("");
}

function createSkipShapes(
  direction: "back" | "forward",
): Shape[] {
  /*
   * Media3's source vectors use a filled 960x960 path rather than a
   * stroked arc.  Keeping that geometry is important at Android Auto's
   * small display sizes: the stroke joins and the arrowhead then match the
   * platform icon exactly.  The generated viewport is 48x48, so all source
   * coordinates are scaled by 1/20.
   */
  const sourcePath = direction === "back"
    ? MEDIA3_SKIP_BACK_PATH
    : MEDIA3_SKIP_FORWARD_PATH;
  const pathData = scalePathData(sourcePath, 1 / 20);

  return [{
    svg: `
      <path
        d="${pathData}"
        fill="currentColor"
      />
    `,
    pathData,
  }];
}


/* -------------------------------------------------------------------------
 * Speedometer
 * ---------------------------------------------------------------------- */

function createSpeedShapes(): Shape[] {
  const cfg = CONFIG.speed;

  const center = {
    x: cfg.centerX,
    y: cfg.centerY,
  };

  const shapes: Shape[] = [];

  /*
   * Main speedometer arc.
   */
  const arc = arcPath(
    center,
    cfg.arc.radius,
    cfg.arc.startAngle,
    cfg.arc.endAngle,
  );

  // shapes.push({
  //   svg: `
  //     <path
  //       d="${arc}"
  //       fill="none"
  //       stroke="currentColor"
  //       stroke-width="${fmt(cfg.arc.strokeWidth)}"
  //       stroke-linecap="round"
  //     />
  //   `,
  //   pathData: arc,
  // });

  /*
   * Optional endpoint ticks.
   */
  if (cfg.ticks.enabled) {
    for (const angle of [
      cfg.arc.startAngle,
      cfg.arc.endAngle,
    ]) {
      const outer = pointOnCircle(
        center,
        cfg.arc.radius,
        angle,
      );

      const direction = normalize(
        subtract(center, outer),
      );

      const inner = add(
        outer,
        multiply(direction, cfg.ticks.length),
      );

      const dx = inner.x - outer.x;
      const dy = inner.y - outer.y;

      const tick = [
        `M ${point(outer)}`,
        `L ${point(inner)}`,
      ].join(" ");

      shapes.push({
        svg: `
          <path
            d="${tick}"
            fill="none"
            stroke="currentColor"
            stroke-width="${fmt(cfg.ticks.width)}"
            stroke-linecap="round"
          />
        `,
        pathData: tick,
      });

      void dx;
      void dy;
    }
  }

  /*
   * Needle.
   */
  if (cfg.needle.enabled) {
    const angle = cfg.needle.angle;

    const tip = pointOnCircle(
      center,
      cfg.needle.length,
      angle,
    );

    const tangent = normalize(
      tangentAtAngle(angle),
    );

    const side = multiply(
      tangent,
      cfg.needle.width / 2,
    );

    const base = pointOnCircle(
      center,
      2,
      angle + 180,
    );

    const needle = [
      `M ${point(subtract(base, side))}`,
      `L ${tip}`,
      `L ${point(add(base, side))}`,
      "Z",
    ].join(" ");

    shapes.push({
      svg: `
        <path
          d="${needle}"
          fill="currentColor"
        />
      `,
      pathData: needle,
    });
  }

  return shapes;
}


/* -------------------------------------------------------------------------
 * SVG output
 * ---------------------------------------------------------------------- */

function makeSvg(
  shapes: Shape[],
  glyphPaths: string[],
): string {
  const width = CONFIG.canvas.width;
  const height = CONFIG.canvas.height;

  const shapeSvg = shapes
    .map(shape => shape.svg)
    .join("\n");

  const textSvg = glyphPaths
    .map(path => `
      <path
        d="${path}"
        fill="currentColor"
      />
    `)
    .join("\n");

  return `<?xml version="1.0" encoding="UTF-8"?>
<svg
  xmlns="http://www.w3.org/2000/svg"
  width="${width}"
  height="${height}"
  viewBox="0 0 ${width} ${height}">

  <g fill="none" color="white">
    ${shapeSvg}
    ${textSvg}
  </g>

</svg>
`;
}


/* -------------------------------------------------------------------------
 * Android VectorDrawable
 * ---------------------------------------------------------------------- */

function escapeXmlAttribute(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function makeVectorDrawable(
  shapes: Shape[],
  glyphPaths: string[],
): string {
  const width = CONFIG.canvas.width;
  const height = CONFIG.canvas.height;

  const paths: string[] = [];

  for (const shape of shapes) {
    if (!shape.pathData) {
      continue;
    }

    /*
     * Stroked paths need VectorDrawable stroke properties.
     *
     * The SVG representation contains the corresponding stroke.
     *
     * Determine the properties based on the shape configuration.
     */
    const isSpeedArc =
      shape.pathData.includes(
        `A ${fmt(CONFIG.speed.arc.radius)}`,
      );

    const isSkipArc =
      shape.pathData.includes(
        `A ${fmt(CONFIG.skip.radius)}`,
      );

    if (isSpeedArc) {
      paths.push(`
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="${fmt(CONFIG.speed.arc.strokeWidth)}"
        android:strokeLineCap="round"
        android:pathData="${escapeXmlAttribute(shape.pathData)}" />`);

      continue;
    }

    if (isSkipArc) {
      paths.push(`
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="${fmt(CONFIG.skip.strokeWidth)}"
        android:strokeLineCap="${CONFIG.skip.lineCap}"
        android:strokeLineJoin="${CONFIG.skip.lineJoin}"
        android:pathData="${escapeXmlAttribute(shape.pathData)}" />`);

      continue;
    }

    /*
     * Filled geometry: arrowhead, ticks and needle.
     */
    paths.push(`
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="${escapeXmlAttribute(shape.pathData)}" />`);
  }

  /*
   * Font glyphs are already actual paths.
   */
  for (const glyph of glyphPaths) {
    paths.push(`
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="${escapeXmlAttribute(glyph)}" />`);
  }

  return `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${width}dp"
    android:height="${height}dp"
    android:viewportWidth="${width}"
    android:viewportHeight="${height}">
${paths.join("\n")}
</vector>
`;
}


/* -------------------------------------------------------------------------
 * Icon construction
 * ---------------------------------------------------------------------- */

function makeSkipIcon(
  direction: "back" | "forward",
  seconds: number,
): GeneratedIcon {
  const shapes = createSkipShapes(direction);

  const number = String(seconds);

  const glyph = media3LabelPath(
    number,
    CONFIG.skip.number.centerX,
    2,
  );

  const name =
    direction === "back"
      ? `ic_skip_back_${seconds}`
      : `ic_skip_forward_${seconds}`;

  return {
    name,

    svg: makeSvg(
      shapes,
      [glyph],
    ),

    vectorDrawable: makeVectorDrawable(
      shapes,
      [glyph],
    ),
  };
}


function makeSpeedIcon(
  speed: number,
): GeneratedIcon {
  const shapes = createSpeedShapes();

  // Keep the useful precision, but omit insignificant trailing zeroes:
  // 0.50x -> 0.5x, 1.00x -> 1x, 1.05x remains 1.05x.
  const text = `${speed.toFixed(2).replace(/\.?0+$/, "")}x`;

  const glyph = media3LabelPath(
    text,
    CONFIG.speed.text.centerX + CONFIG.speed.text.offsetX,
    1,
  );

  const value = speed.toFixed(2);

  return {
    name: `ic_speed_${value.replace(".", "_")}x`,

    svg: makeSvg(
      shapes,
      [glyph],
    ),

    vectorDrawable: makeVectorDrawable(
      shapes,
      [glyph],
    ),
  };
}


/* -------------------------------------------------------------------------
 * File generation
 * ---------------------------------------------------------------------- */

async function writeIcon(
  icon: GeneratedIcon,
): Promise<void> {
  const svgPath = join(
    CONFIG.outputDir,
    "svg",
    `${icon.name}.svg`,
  );

  const xmlPath = join(
    CONFIG.outputDir,
    "drawable",
    `${icon.name}.xml`,
  );

  await mkdir(dirname(svgPath), {
    recursive: true,
  });

  await mkdir(dirname(xmlPath), {
    recursive: true,
  });

  await writeFile(
    svgPath,
    icon.svg,
    "utf8",
  );

  await writeFile(
    xmlPath,
    icon.vectorDrawable,
    "utf8",
  );
}


/* -------------------------------------------------------------------------
 * Main
 * ---------------------------------------------------------------------- */

async function main(): Promise<void> {
  let count = 0;

  /*
   * Skip backward.
   */
  for (
    let seconds = CONFIG.values.skipFrom;
    seconds <= CONFIG.values.skipTo;
    seconds += CONFIG.values.skipStep
  ) {
    const icon = makeSkipIcon(
      "back",
      seconds,
    );

    await writeIcon(icon);

    count++;

    console.log(`  ${icon.name}`);
  }

  /*
   * Skip forward.
   */
  for (
    let seconds = CONFIG.values.skipFrom;
    seconds <= CONFIG.values.skipTo;
    seconds += CONFIG.values.skipStep
  ) {
    const icon = makeSkipIcon(
      "forward",
      seconds,
    );

    await writeIcon(icon);

    count++;

    console.log(`  ${icon.name}`);
  }

  /*
   * Speed.
   *
   * Don't accumulate floating-point error by repeatedly doing:
   *
   *   speed += 0.05
   *
   * Instead use an integer number of steps.
   */
  const speedSteps = Math.round(
    (CONFIG.values.speedTo -
      CONFIG.values.speedFrom) /
    CONFIG.values.speedStep,
  );

  for (
    let i = 0;
    i <= speedSteps;
    i++
  ) {
    const speed =
      CONFIG.values.speedFrom +
      i * CONFIG.values.speedStep;

    const roundedSpeed =
      Number(speed.toFixed(2));

    const icon = makeSpeedIcon(
      roundedSpeed,
    );

    await writeIcon(icon);

    count++;

    console.log(`  ${icon.name}`);
  }

  console.log();
  console.log(`Generated ${count} icons.`);
  console.log(
    `SVG:       ${join(CONFIG.outputDir, "svg")}`,
  );
  console.log(
    `Android:   ${join(CONFIG.outputDir, "drawable")}`,
  );
}


main().catch(error => {
  console.error(error);
  process.exit(1);
});
