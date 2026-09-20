package riscvconsole.fpga.vcu108

import sifive.fpgashells.shell.xilinx._

import scala.collection.immutable.HashMap

object FMC1Map {
  def apply(fmcPin: String): String = HashMap(
    "A2" -> "H2",
    "A3" -> "H1",
    "B4" -> "W4",
    "A6" -> "F2",
    "B5" -> "W3",
    "A7" -> "F1",
    "B8" -> "Y2",
    "A10" -> "D2",
    "B9" -> "Y1",
    "A11" -> "D1",
    "B12" -> "M2",
    "A14" -> "T2",
    "B13" -> "M1",
    "A15" -> "T1",
    "B16" -> "P2",
    "A18" -> "R4",
    "B17" -> "P1",
    "A19" -> "R3",
    "B20" -> "N9",
    "A22" -> "F7",
    "B21" -> "N8",
    "A23" -> "F6",
    "B24" -> "T7",
    "A26" -> "E5",
    "B25" -> "T6",
    "A27" -> "E4",
    "B28" -> "V7",
    "A30" -> "C5",
    "B29" -> "V6",
    "A31" -> "C4",
    "B32" -> "H7",
    "A34" -> "L5",
    "B33" -> "H6",
    "A35" -> "L4",
    "B36" -> "J5",
    "A38" -> "K7",
    "B37" -> "J4",
    "A39" -> "K6",
    "C2" -> "G5",
    "C3" -> "G4",
    "D4" -> "R9",
    "C6" -> "K2",
    "D5" -> "R8",
    "C7" -> "K1",
    "D8" -> "BC10",
    "C10" -> "BE10",
    "D9" -> "BD10",
    "C11" -> "BE9",
    "D11" -> "BF12",
    "C14" -> "BE14",
    "D12" -> "BF11",
    "C15" -> "BF14",
    "D14" -> "BD13",
    "C18" -> "BB13",
    "D15" -> "BE13",
    "C19" -> "BB12",
    "D17" -> "BA14",
    "C22" -> "AP13",
    "D18" -> "BB14",
    "C23" -> "AR13",
    "D20" -> "AV14",
    "C26" -> "AN14",
    "D21" -> "AV13",
    "C27" -> "AN13",
    "D23" -> "AT16",
    "C30" -> "U80.9",
    "D24" -> "AT15",
    "C31" -> "U80.8",
    "D26" -> "AL14",
    "D27" -> "AM14",
    "E2" -> "T14",
    "F1" -> "U44.18",
    "E3" -> "R13",
    "F4" -> "N14",
    "E6" -> "AA12",
    "F5" -> "N13",
    "E7" -> "Y12",
    "F7" -> "AA13",
    "E9" -> "AA14",
    "F8" -> "Y13",
    "E10" -> "Y14",
    "F10" -> "W12",
    "E12" -> "W14",
    "F11" -> "V12",
    "E13" -> "V14",
    "F13" -> "V15",
    "E15" -> "V16",
    "F14" -> "U15",
    "E16" -> "U16",
    "F16" -> "V13",
    "E18" -> "R11",
    "F17" -> "U12",
    "E19" -> "P11",
    "F19" -> "R14",
    "F20" -> "P14",
    "G2" -> "AU14",
    "G3" -> "AU13",
    "H2" -> "AL19",
    "G6" -> "AY9",
    "H4" -> "BB9",
    "G7" -> "BA9",
    "H5" -> "BB8",
    "G9" -> "BD8",
    "H7" -> "BA7",
    "G10" -> "BD7",
    "H8" -> "BB7",
    "G12" -> "BF10",
    "H10" -> "BE8",
    "G13" -> "BF9",
    "H11" -> "BE7",
    "G15" -> "BE15",
    "H13" -> "BD12",
    "G16" -> "BF15",
    "H14" -> "BE12",
    "G18" -> "AY8",
    "H16" -> "BC11",
    "G19" -> "AY7",
    "H17" -> "BD11",
    "G21" -> "AY15",
    "H19" -> "AV9",
    "G22" -> "AY14",
    "H20" -> "AV8",
    "G24" -> "AN15",
    "H22" -> "AV15",
    "G25" -> "AP15",
    "H23" -> "AW15",
    "G27" -> "AM13",
    "H25" -> "AN16",
    "G28" -> "AM12",
    "H26" -> "AP16",
    "G30" -> "AK14",
    "H28" -> "AK15",
    "G31" -> "AK13",
    "H29" -> "AL15",
    "G33" -> "AP12",
    "H31" -> "AJ13",
    "G34" -> "AR12",
    "H32" -> "AJ12",
    "G36" -> "AU16",
    "H34" -> "AK12",
    "G37" -> "AV16",
    "H35" -> "AL12",
    "H37" -> "AU11",
    "H38" -> "AV11",
    "J6" -> "K11",
    "J7" -> "J11",
    "K7" -> "T16",
    "J9" -> "R12",
    "K8" -> "T15",
    "J10" -> "P12",
    "K10" -> "P15",
    "J12" -> "M11",
    "K11" -> "N15",
    "J13" -> "L11",
    "K13" -> "K12",
    "J15" -> "K14",
    "K14" -> "J12",
    "J16" -> "K13",
    "K16" -> "U13",
    "J18" -> "L14",
    "K17" -> "T13",
    "J19" -> "L13",
    "K19" -> "M13",
    "J21" -> "M15",
    "K20" -> "M12",
    "J22" -> "L15",
    "K22" -> "U11",
    "K23" -> "T11",
  )(fmcPin)
}

object FMC2Map {
  def apply(fmcPin: String): String = HashMap(
    "A2" -> "AG4",
    "A3" -> "AG3",
    "B4" -> "W4",
    "A6" -> "AF2",
    "B5" -> "W3",
    "A7" -> "AF1",
    "B8" -> "Y2",
    "A10" -> "AE4",
    "B9" -> "Y1",
    "A11" -> "AE3",
    "B12" -> "AA4",
    "A14" -> "AD2",
    "B13" -> "AA3",
    "A15" -> "AD1",
    "B16" -> "AB2",
    "A18" -> "AC4",
    "B17" -> "AB1",
    "A19" -> "AC3",
    "B20" -> "AA9",
    "A22" -> "AM7",
    "B21" -> "AA8",
    "A23" -> "AM6",
    "B24" -> "T7",
    "A26" -> "AK7",
    "B25" -> "T6",
    "A27" -> "AK6",
    "B28" -> "V7",
    "A30" -> "AH7",
    "B29" -> "V6",
    "A31" -> "AH6",
    "B32" -> "Y7",
    "A34" -> "AF7",
    "B33" -> "Y6",
    "A35" -> "AF6",
    "B36" -> "AB7",
    "A38" -> "AD7",
    "B37" -> "AB6",
    "A39" -> "AD6",
    "C2" -> "AN5",
    "C3" -> "AN4",
    "C6" -> "AH2",
    "C7" -> "AH1",
    "D8" -> "P35",
    "C10" -> "P37",
    "D9" -> "P36",
    "C11" -> "N37",
    "D11" -> "N38",
    "C14" -> "N32",
    "D12" -> "M38",
    "C15" -> "M32",
    "D14" -> "M36",
    "C18" -> "L33",
    "D15" -> "L36",
    "C19" -> "K33",
    "D17" -> "T30",
    "C22" -> "AL32",
    "D18" -> "T31",
    "C23" -> "AM32",
    "D20" -> "AJ32",
    "C26" -> "AP35",
    "D21" -> "AK32",
    "C27" -> "AR35",
    "D23" -> "AN33",
    "D24" -> "AP33",
    "D26" -> "AL29",
    "D27" -> "AM29",
    "F1" -> "AU24",
    "G2" -> "AK34",
    "G3" -> "AL34",
    "H2" -> "BD23",
    "G6" -> "T33",
    "H4" -> "R32",
    "G7" -> "R33",
    "H5" -> "P32",
    "G9" -> "N34",
    "H7" -> "N33",
    "G10" -> "N35",
    "H8" -> "M33",
    "G12" -> "M35",
    "H10" -> "M37",
    "G13" -> "L35",
    "H11" -> "L38",
    "G15" -> "R31",
    "H13" -> "L34",
    "G16" -> "P31",
    "H14" -> "K34",
    "G18" -> "U31",
    "H16" -> "Y31",
    "G19" -> "U32",
    "H17" -> "W31",
    "G21" -> "AR37",
    "H19" -> "T34",
    "G22" -> "AT37",
    "H20" -> "T35",
    "G24" -> "AL30",
    "H22" -> "AT39",
    "G25" -> "AL31",
    "H23" -> "AT40",
    "G27" -> "AP36",
    "H25" -> "AT35",
    "G28" -> "AP37",
    "H26" -> "AT36",
    "G30" -> "AP38",
    "H28" -> "AM36",
    "G31" -> "AR38",
    "H29" -> "AN36",
    "G33" -> "AN34",
    "H31" -> "AL35",
    "G34" -> "AN35",
    "H32" -> "AL36",
    "G36" -> "AG32",
    "H34" -> "AJ30",
    "G37" -> "AG33",
    "H35" -> "AJ31",
    "H37" -> "AG31",
    "H38" -> "AH31",
  )(fmcPin)
}

case class VCU108LOC2FMCGroup(fmc: (String) => String,  elem: Seq[String]) extends VCU108Elem {
  val GetBindings = elem.map{case i => fmc(i)}
  val GetStandard = "LVCMOS18"
}

object TerasicFMC2GPIO {
  def apply(gpio: (Int, Int), fmc: (String) => String): String =
    fmc(
      Seq(
        Seq(
          "H4", // GPIO_0_D0, CLK0_M2C_P
          "H5", // GPIO_0_D1, CLK0_M2C_N
          "D8", // GPIO_0_D2, LA01_P_CC
          "D9", // GPIO_0_D3, LA01_N_CC
          "H16", // GPIO_0_D4, LA11_P
          "H17", // GPIO_0_D5, LA11_N
          "H19", // GPIO_0_D6, LA15_P
          "H20", // GPIO_0_D7, LA15_N
          "D14", // GPIO_0_D8, LA09_P
          "D15", // GPIO_0_D9, LA09_N
          "D17", // GPIO_0_D10, LA13_P
          "D18", // GPIO_0_D11, LA13_N
          "D20", // GPIO_0_D12, LA17_P_CC
          "D21", // GPIO_0_D13, LA17_N_CC
          "H22", // GPIO_0_D14, LA19_P
          "H23", // GPIO_0_D15, LA19_N
          "H25", // GPIO_0_D16, LA21_P
          "H26", // GPIO_0_D17, LA21_N
          "D23", // GPIO_0_D18, LA23_P
          "D24", // GPIO_0_D19, LA23_N
          "H13", // GPIO_0_D20, LA07_P
          "H14", // GPIO_0_D21, LA07_N
          "H10", // GPIO_0_D22, LA04_P
          "H11", // GPIO_0_D23, LA04_N
          "H7", // GPIO_0_D24, LA02_P
          "H8", // GPIO_0_D25, LA02_N
          "D11", // GPIO_0_D26, LA05_P
          "D12", // GPIO_0_D27, LA05_N
          "H28", // GPIO_0_D28, LA24_P
          "H29", // GPIO_0_D29, LA24_N
          "D26", // GPIO_0_D30, LA26_P
          "D27", // GPIO_0_D31, LA26_N
          "H31", // GPIO_0_D32, LA28_P
          "H32", // GPIO_0_D33, LA28_N
          "H34", // GPIO_0_D34, LA30_P
          "H35", // GPIO_0_D35, LA30_N
        ),
        Seq(
          "G15", // GPIO_1_D0, LA12_P
          "G16", // GPIO_1_D1, LA12_N
          "G6", // GPIO_1_D2, LA00_P_CC
          "G7", // GPIO_1_D3, LA00_N_CC
          "G12", // GPIO_1_D4, LA08_P
          "G13", // GPIO_1_D5, LA08_N
          "G9", // GPIO_1_D6, LA03_P
          "G10", // GPIO_1_D7, LA03_N
          "K7", // GPIO_1_D8, HA02_P
          "K8", // GPIO_1_D9, HA02_N
          "C10", // GPIO_1_D10, LA06_P
          "C11", // GPIO_1_D11, LA06_N
          "C14", // GPIO_1_D12, LA10_P
          "C15", // GPIO_1_D13, LA10_N
          "G18", // GPIO_1_D14, LA16_P
          "G19", // GPIO_1_D15, LA16_N
          "C18", // GPIO_1_D16, LA14_P
          "C19", // GPIO_1_D17, LA14_N
          "G21", // GPIO_1_D18, LA20_P
          "G22", // GPIO_1_D19, LA20_N
          "C22", // GPIO_1_D20, LA18_P_CC
          "C23", // GPIO_1_D21, LA18_N_CC
          "G24", // GPIO_1_D22, LA22_P
          "G25", // GPIO_1_D23, LA22_N
          "G27", // GPIO_1_D24, LA25_P
          "G28", // GPIO_1_D25, LA25_N
          "C26", // GPIO_1_D26, LA27_P
          "C27", // GPIO_1_D27, LA27_N
          "G30", // GPIO_1_D28, LA29_P
          "G31", // GPIO_1_D29, LA29_N
          "G33", // GPIO_1_D30, LA31_P
          "G34", // GPIO_1_D31, LA31_N
          "G36", // GPIO_1_D32, LA33_P
          "G37", // GPIO_1_D33, LA33_N
          "H37", // GPIO_1_D34, LA32_P
          "H38", // GPIO_1_D35, LA32_N
        ),
        Seq(
          "E2", // GPIO_2_D0, HA01_P_CC
          "E3", // GPIO_2_D1, HA01_N_CC
          "F4", // GPIO_2_D2, HA00_P_CC
          "F5", // GPIO_2_D3, HA00_N_CC
          "E6", // GPIO_2_D4, HA05_P
          "E7", // GPIO_2_D5, HA05_N
          "E9", // GPIO_2_D6, HA09_P
          "E10", // GPIO_2_D7, HA09_N
          "E12", // GPIO_2_D8, HA13_P
          "E13", // GPIO_2_D9, HA13_N
          "E15", // GPIO_2_D10, HA16_P
          "E16", // GPIO_2_D11, HA16_N
          "E18", // GPIO_2_D12, HA20_P
          "E19", // GPIO_2_D13, HA20_N
          "J6", // GPIO_2_D14, HA03_P
          "J7", // GPIO_2_D15, HA03_N
          "J12", // GPIO_2_D16, HA11_P
          "J13", // GPIO_2_D17, HA11_N
          "J18", // GPIO_2_D18, HA18_P
          "J19", // GPIO_2_D19, HA18_N
          "F7", // GPIO_2_D20, HA04_P
          "F8", // GPIO_2_D21, HA04_N
          "F10", // GPIO_2_D22, HA08_P
          "F11", // GPIO_2_D23, HA08_N
          "F13", // GPIO_2_D24, HA12_P
          "F14", // GPIO_2_D25, HA12_N
          "F16", // GPIO_2_D26, HA15_P
          "F17", // GPIO_2_D27, HA15_N
          "F19", // GPIO_2_D28, HA19_P
          "F20", // GPIO_2_D29, HA19_N
          "J2", // GPIO_2_D30, CLK3_BIDIR_P
          "J3", // GPIO_2_D31, CLK3_BIDIR_N
          "J9", // GPIO_2_D32, HA07_P
          "J10", // GPIO_2_D33, HA07_N
          "J15", // GPIO_2_D34, HA14_P
          "J16", // GPIO_2_D35, HA14_N
        ),
        Seq(
          "E36", // GPIO_3_D0, HB21_P
          "E37", // GPIO_3_D1, HB21_N
          "F37", // GPIO_3_D2, HB20_P
          "F38", // GPIO_3_D3, HB20_N
          "E21", // GPIO_3_D4, HB03_P
          "E22", // GPIO_3_D5, HB03_N
          "E24", // GPIO_3_D6, HB05_P
          "E25", // GPIO_3_D7, HB05_N
          "E27", // GPIO_3_D8, HB09_P
          "E28", // GPIO_3_D9, HB09_N
          "E30", // GPIO_3_D10, HB13_P
          "E31", // GPIO_3_D11, HB13_N
          "E33", // GPIO_3_D12, HB19_P
          "E34", // GPIO_3_D13, HB19_N
          "J21", // GPIO_3_D14, HA22_P
          "J22", // GPIO_3_D15, HA22_N
          "J27", // GPIO_3_D16, HB07_P
          "J28", // GPIO_3_D17, HB07_N
          "J33", // GPIO_3_D18, HB15_P
          "J34", // GPIO_3_D19, HB15_N
          "F22", // GPIO_3_D20, HB02_P
          "F23", // GPIO_3_D21, HB02_N
          "F25", // GPIO_3_D22, HB04_P
          "F26", // GPIO_3_D23, HB04_N
          "F28", // GPIO_3_D24, HB08_P
          "F29", // GPIO_3_D25, HB08_N
          "F31", // GPIO_3_D26, HB12_P
          "F32", // GPIO_3_D27, HB12_N
          "F34", // GPIO_3_D28, HB16_P
          "F35", // GPIO_3_D29, HB16_N
          "J24", // GPIO_3_D30, HB01_P
          "J25", // GPIO_3_D31, HB01_N
          "J30", // GPIO_3_D32, HB11_P
          "J31", // GPIO_3_D33, HB11_N
          "J36", // GPIO_3_D34, HB18_P
          "J37", // GPIO_3_D35, HB18_N
        ),
      )(gpio._1)(gpio._2)
    )
}