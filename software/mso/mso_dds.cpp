// Drive the ADC input with the MSO DDS and check the generated signal with the
// oscilloscope channels. Uses the single device API of VmMsoOpenSDK.
//
// Usage: mso_dds [freq_hz] [ampl_mv] [bias_mv] [hold_s] [csv] [wave]
//   hold_s   seconds to keep the DDS running after the measurement (0 = until Ctrl-C)
//   csv      file to save the captured scope samples ("-" for none)
//   wave     sine (default), ramp (triangle, 50% duty), square
//
// Environment: SCOPE_MV  scope range +-SCOPE_MV (default 1000)
#include <atomic>
#include <chrono>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <thread>
#include <vector>
#include "VmMsoLib.h"

static std::atomic<int> added{0}, ready{0}, stop{0};
static void CALLBACK on_add(void*) { added = 1; }
static void CALLBACK on_rmv(void*) { added = 0; }
static void CALLBACK on_data(void*) { ready++; }
static void on_sigint(int) { stop = 1; }

static bool wait_for(std::atomic<int>& v, int ms)
{
    for (int i = 0; i < ms / 10 && !v; i++)
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    return v != 0;
}

int main(int argc, char** argv)
{
    double freq = argc > 1 ? atof(argv[1]) : 1000.0;
    int ampl = argc > 2 ? atoi(argv[2]) : 400;
    int bias = argc > 3 ? atoi(argv[3]) : 600;
    int hold = argc > 4 ? atoi(argv[4]) : 0;
    const char* csv = argc > 5 && strcmp(argv[5], "-") ? argv[5] : nullptr;
    const char* wave = argc > 6 ? argv[6] : "sine";
    unsigned box = !strcmp(wave, "ramp") ? BX_RAMP : !strcmp(wave, "square") ? BX_SQUARE : BX_SINE;

    InitDll(0, 0);
    SetDevNoticeCallBack(nullptr, on_add, on_rmv);
    SetDataReadyCallBack(nullptr, on_data);
    ScanDevice();
    if (!wait_for(added, 5000) && !IsDevAvailable()) {
        printf("No MSO device\n");
        FinishDll();
        return 1;
    }
    char des[256] = {0};
    GetDeviceDesString(des, sizeof des);
    printf("Device: %s\n", des);
    if (!IsSupportDDSDevice()) {
        printf("Device has no DDS\n");
        FinishDll();
        return 1;
    }
    printf("DDS %s bias range: [%d, %d] mV, max ampl %d mV\n", wave, GetDDSCurBoxingBiasMvMin(box),
           GetDDSCurBoxingBiasMvMax(box), GetDDSCurBoxingAmplitudeMv(box));

    // DDS: continuous waveform on channel 1
    SetDDSBoxingStyle(0, box);
    SetDDSFreq(0, freq);
    SetDDSDutyCycle(0, 50.0);
    SetDDSAmplitudeMv(0, ampl);
    SetDDSBiasMv(0, bias);
    SetDDSOutMode(0, DDS_OUT_MODE_CONTINUOUS);
    DDSOutputEnable(0, 1);
    printf("DDS0: %s freq=%.3f Hz ampl=%d mV bias=%d mV en=%d\n", wave, GetDDSFreq(0), GetDDSAmplitudeMv(0), GetDDSBiasMv(0),
           IsDDSOutputEnable(0));

    // Scope: both channels DC, +-SCOPE_MV, a sample rate that fits ~20 periods in the capture
    int nch = GetOscChannelNum();
    int scope_mv = getenv("SCOPE_MV") ? atoi(getenv("SCOPE_MV")) : 1000;
    for (int c = 0; c < nch; c++) {
        SetOscChannelRangemV(c, -scope_mv, scope_mv);
        if (IsSupportAcDc(c))
            SetAcDc(c, 0);
    }
    const int len_kb = 8;
    int ns = GetOscSupportSampleRateNum();
    std::vector<unsigned> rates(ns > 0 ? ns : 1);
    GetOscSupportSampleRates(rates.data(), ns);
    unsigned want = (unsigned)(freq * len_kb * 1024 / 20);
    unsigned rate = rates[0];
    for (int i = 0; i < ns; i++)
        if (rates[i] <= want && rates[i] > rate)
            rate = rates[i];
    SetOscSampleRate(rate);
    SetTriggerMode(TRIGGER_MODE_AUTO);
    SetTriggerStyle(TRIGGER_STYLE_RISE_EDGE);
    SetTriggerSource(TRIGGER_SOURCE_CH1);
    SetTriggerLevelmV(0, bias);
    SetPreTriggerPercent(50);
    std::this_thread::sleep_for(std::chrono::milliseconds(300));

    ready = 0;
    int got_kb = Capture(len_kb, (1 << nch) - 1, 1);
    if (!wait_for(ready, 5000)) {
        printf("Timeout waiting for the capture\n");
    } else {
        printf("Scope: %u samples/s\n", GetOscSampleRate());
        std::vector<std::vector<double>> v(nch, std::vector<double>(got_kb * 1024 + 16));
        std::vector<unsigned> n(nch);
        for (int c = 0; c < nch; c++) {
            n[c] = ReadVoltageDatas(c, v[c].data(), v[c].size());
            double mn = 1e9, mx = -1e9, avg = 0;
            for (unsigned i = 0; i < n[c]; i++) {
                mn = v[c][i] < mn ? v[c][i] : mn;
                mx = v[c][i] > mx ? v[c][i] : mx;
                avg += v[c][i];
            }
            if (n[c])
                avg /= n[c];
            printf("CH%d: n=%u min=%.3f V max=%.3f V avg=%.3f V pk-pk=%.3f V", c + 1, n[c], mn, mx, avg, mx - mn);
            if (CalFreq(v[c].data(), n[c], GetVoltageResolution(c), GetOscSampleRate()))
                printf(" freq=%.2f Hz", GetFreq());
            if (IsVoltageDatasOutRange(c))
                printf(" OUT OF RANGE");
            printf("\n");
        }
        if (csv) {
            FILE* f = fopen(csv, "w");
            if (f) {
                fprintf(f, "t");
                for (int c = 0; c < nch; c++)
                    fprintf(f, ",ch%d", c + 1);
                fprintf(f, "\n");
                for (unsigned i = 0; i < n[0]; i++) {
                    fprintf(f, "%.9g", (double)i / GetOscSampleRate());
                    for (int c = 0; c < nch; c++)
                        fprintf(f, ",%.4f", i < n[c] ? v[c][i] : 0.0);
                    fprintf(f, "\n");
                }
                fclose(f);
                printf("Saved %s\n", csv);
            }
        }
    }

    // Keep the DDS running for the ADC capture
    signal(SIGINT, on_sigint);
    signal(SIGTERM, on_sigint);
    printf(hold ? "DDS running for %d s\n" : "DDS running, Ctrl-C to stop\n", hold);
    fflush(stdout);
    for (int t = 0; !stop && (!hold || t < hold * 10); t++)
        std::this_thread::sleep_for(std::chrono::milliseconds(100));

    DDSOutputEnable(0, 0);
    FinishDll();
    return 0;
}
