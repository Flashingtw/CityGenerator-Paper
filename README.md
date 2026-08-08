# CityGenerator

由 `System.dsc` 轉換並重新設計而成的 Paper 26.2 原生 Java 外掛，使用 WorldEdit／FAWE 貼上 Sponge `.schem`。

城市以隨機 BFS 從中心向外擴張。指令大小代表路口節點總數，路口間具有隨機長度的直路，並能形成少量自然閉環。

## 安裝

1. 伺服器使用 Paper 26.2 與 Java 25。
2. 安裝 WorldEdit 7.4.4 或相容的 FastAsyncWorldEdit。
3. 將 `CityGenerator-2.2.0.jar` 放進伺服器的 `plugins` 目錄並啟動一次。
4. 將以下檔案放到 `plugins/CityGenerator/schematics/`：
   - `deadend.schem`
   - `straight.schem`
   - `corner.schem`
   - `t_junction.schem`
   - `cross.schem`
5. 重新啟動伺服器，或直接執行指令（每次生成前都會重新載入 schematic）。

## 指令與權限

- `/citycreate <路口節點數>`：以玩家腳下方塊為中心生成指定數量路口的城市；權限 `dgspawn.use`。節點間的 `straight` 不計入數量。
- `/stopdungeon`：停止該玩家目前的生成工作；權限 `stopdungeon.use`。

預設只有 OP 擁有兩項權限。模板單位、節點上限、每 tick 貼上數量、路段長度、道路間距與自然閉環，可在 `config.yml` 調整。

## 建置

```powershell
.\gradlew.bat build
```

輸出位於 `build/libs/CityGenerator-2.2.0.jar`。
