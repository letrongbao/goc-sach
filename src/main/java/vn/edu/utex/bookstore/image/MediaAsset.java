package vn.edu.utex.bookstore.image;

import java.time.Instant;

/** Cloudinary metadata retained so replacement/deletion can be audited safely. */
public class MediaAsset {
  public Long id;
  public String publicId, resourceType, secureUrl, status;
  public Long ownerId, shopId, productId;
  public long bytes;
  public Integer width, height;
  public Instant createdAt, updatedAt;
}
