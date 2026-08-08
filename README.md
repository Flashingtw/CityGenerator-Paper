# CityGenerator-Paper

Paper 26.2 原生 Java 城市生成插件。使用 WorldEdit／FAWE 貼上 Sponge `.schem` 模板，透過隨機圖形生成具有道路、路口與自然環狀結構的城市。

## 支援環境

- Paper 26.2
- Java 25
- WorldEdit 7.4.4 或 FastAsyncWorldEdit 2.15.3+

## 安裝

1. 建置插件：

   ```powershell
   .\gradlew.bat build
   ```

2. 將 `build/libs/CityGenerator-2.2.0.jar` 放入伺服器的 `plugins` 目錄。
3. 啟動伺服器一次，然後將以下模板放入 `plugins/CityGenerator/schematics/`：

   - `deadend.schem`
   - `straight.schem`
   - `corner.schem`
   - `t_junction.schem`
   - `cross.schem`

## 指令

- `/citycreate <路口節點數>`：生成指定數量的路口節點。節點類型包含死路、轉彎、T 字路口與十字路口。
- `/stopdungeon`：停止目前的城市生成工作。

權限：`dgspawn.use`、`stopdungeon.use`。

## 設定

可在 `plugins/CityGenerator/config.yml` 調整道路間距、邊長、環路機率、最大節點數與每 tick 貼上數量。

## 插件名稱

GitHub 專案名稱為 `CityGenerator-Paper`；伺服器內插件名稱仍為 `CityGenerator`，因此不會影響既有資料夾、指令或設定檔。
