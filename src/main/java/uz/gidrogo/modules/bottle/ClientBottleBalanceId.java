package uz.gidrogo.modules.bottle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientBottleBalanceId implements Serializable {
    private Long clientId;
    private Long farmId;
    private Long productId;
}
